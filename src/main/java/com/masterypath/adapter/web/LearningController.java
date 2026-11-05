package com.masterypath.adapter.web;

import com.masterypath.adapter.llm.TaskModel;
import com.masterypath.domain.mode.SessionMode;
import com.masterypath.domain.model.CoverageReport;
import com.masterypath.domain.model.LearningModule;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.QuestionBankEntry;
import com.masterypath.domain.model.SourceGap;
import com.masterypath.domain.model.TopicSource;
import com.masterypath.domain.outline.BuildResult;
import com.masterypath.domain.outline.OutlineException;
import com.masterypath.domain.outline.ReviseResult;
import com.masterypath.knowledge.FolderScanner;
import com.masterypath.service.KnowledgeService;
import com.masterypath.service.LearningService;
import com.masterypath.storage.KnowledgeStore;
import com.masterypath.storage.LearningStore;
import com.masterypath.storage.PreconditionFailedException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 路径、地图、进度、事件与题库端点（参考实现 {@code api/app.py} 133–387 行）。
 *
 * <p>错误码逐端点显式映射，不用全局一刀切（{@code design.md} 决策 15）：
 * <ul>
 *   <li>{@code override} 用 <b>412</b>——全项目唯一。</li>
 *   <li>{@code rename} / {@code redo} / {@code review-settings} 把前置条件失败映射成 <b>404</b>。</li>
 *   <li>{@code gaps/resolve} 与 {@code proposals/resolve} 把两类异常都映射成 <b>422</b>。</li>
 * </ul>
 */
@RestController
@RequestMapping("/learning")
public class LearningController {

    private final LearningService service;
    private final LearningStore store;
    private final KnowledgeService knowledgeService;
    private final TaskModel taskModel;

    public LearningController(LearningService service, LearningStore store,
                              KnowledgeService knowledgeService) {
        this(service, store, knowledgeService, new TaskModel());
    }

    /**
     * @param taskModel 「建议下一问」的草稿生成器；{@code null} 时用无模型版本（恒回退）
     */
    @Autowired
    public LearningController(LearningService service, LearningStore store,
                              KnowledgeService knowledgeService, TaskModel taskModel) {
        this.service = service;
        this.store = store;
        this.knowledgeService = knowledgeService;
        this.taskModel = taskModel == null ? new TaskModel() : taskModel;
    }

    // -- 路径 ----------------------------------------------------------------

    /**
     * 路径列表（参考实现 {@code list_paths}）。
     *
     * <p><b>与 {@code listPathOverviews} 不同</b>：这里只跳过「有 owner 且无知识点」的路径
     * （会话私有 scratch），普通空路径仍会列出。两者不可互换。
     */
    @GetMapping("/paths")
    public List<Map<String, Object>> listPaths() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LearningProgress progress : store.listPaths()) {
            if (!progress.getOwnerSessionId().isEmpty() && !hasKnowledgePoints(progress)) {
                continue;
            }
            Map<String, Object> state = service.getState(progress.getPathId());
            rows.add(ApiSupport.payload(
                    "path_id", progress.getPathId(),
                    "name", progress.getName(),
                    "goal", progress.getGoal(),
                    "objectives", objectiveCount(progress),
                    "mastered", countsOf(state).get("mastered"),
                    "due_reviews", listOf(state.get("due_reviews")).size()));
        }
        return rows;
    }

    /** 创建路径；返回 201 + 完整 state。 */
    @PostMapping("/paths")
    public ResponseEntity<Map<String, Object>> createPath(
            @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        String pathId = ApiSupport.str(body.get("path_id"));
        if (pathId.isEmpty()) {
            throw new ApiException(422, "path_id required");
        }
        try {
            service.createPath(pathId,
                    ApiSupport.str(body.get("goal")),
                    ApiSupport.str(body.get("name")));
        } catch (PreconditionFailedException e) {
            throw new ApiException(409, e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.getState(pathId));
    }

    @GetMapping("/paths/{pathId}")
    public Map<String, Object> getPath(@PathVariable String pathId) {
        Map<String, Object> state = service.getState(pathId);
        if ("missing".equals(state.get("status"))) {
            throw new ApiException(404, "unknown path");
        }
        return state;
    }

    /** 删除路径；参考实现不校验存在性，恒 204。 */
    @DeleteMapping("/paths/{pathId}")
    public ResponseEntity<Void> deletePath(@PathVariable String pathId) {
        service.delete(pathId);
        return ResponseEntity.noContent().build();
    }

    /** 改名；无 {@code name} 键时退化为读状态。 */
    @PatchMapping("/paths/{pathId}")
    public Map<String, Object> patchPath(@PathVariable String pathId,
                                         @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        if (body.containsKey("name")) {
            try {
                return service.rename(pathId, ApiSupport.str(body.get("name")));
            } catch (PreconditionFailedException e) {
                throw new ApiException(404, e.getMessage());
            }
        }
        return service.getState(pathId);
    }

    /** 清空学习态但保留地图。 */
    @PostMapping("/paths/{pathId}/redo")
    public Map<String, Object> redo(@PathVariable String pathId) {
        try {
            return service.reset(pathId);
        } catch (PreconditionFailedException e) {
            throw new ApiException(404, e.getMessage());
        }
    }

    /** 复习保留率；缺省 0.9（Python 的 {@code float(x or 0.9)}）。 */
    @PutMapping("/paths/{pathId}/review-settings")
    public Map<String, Object> reviewSettings(@PathVariable String pathId,
                                              @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        Object raw = body.get("desired_retention");
        double retention = ApiSupport.bool(raw) ? ApiSupport.doubleOr(raw, 0.9) : 0.9;
        try {
            return service.setReviewSettings(pathId, retention);
        } catch (PreconditionFailedException e) {
            throw new ApiException(404, e.getMessage());
        }
    }

    // -- 地图（仅 outline 模式） ----------------------------------------------

    /**
     * 写入地图：有 {@code ops} 走 {@code revise}，否则走 {@code build}。
     *
     * <p>模式门控在调用服务<b>之前</b>：未记录模式不门控（{@code INV-09}）。
     */
    @PutMapping("/paths/{pathId}/map")
    public Map<String, Object> putMap(@PathVariable String pathId,
                                      @RequestParam(name = "session_id", required = false) String sessionId,
                                      @RequestBody(required = false) Map<String, Object> payload) {
        requireSessionId(sessionId);
        requireOutline(sessionId);
        Map<String, Object> body = payload == null ? Map.of() : payload;
        try {
            if (body.containsKey("ops")) {
                ReviseResult result = service.revise(pathId, asOps(body.get("ops")));
                return ApiSupport.payload("status", result.status(), "applied", result.applied());
            }
            BuildResult result = service.build(pathId,
                    ApiSupport.payload("modules", body.get("modules")),
                    ApiSupport.bool(body.get("confirm")));
            if (BuildResult.REFUSED_REBUILD.equals(result.status())) {
                throw new ApiException(409, "rebuild_requires_confirm");
            }
            return ApiSupport.payload("status", result.status(),
                    "version", service.getState(pathId).get("version"));
        } catch (OutlineException e) {
            throw new ApiException(422, e.getMessage());
        }
    }

    /**
     * 重建地图；必须显式 {@code confirm}。
     *
     * <p>判定在调用服务<b>之前</b>——否则服务已写库才拒绝（{@code design.md} 风险表）。
     */
    @PostMapping("/paths/{pathId}/rebuild")
    public Map<String, Object> rebuild(@PathVariable String pathId,
                                       @RequestParam(name = "session_id", required = false) String sessionId,
                                       @RequestBody(required = false) Map<String, Object> payload) {
        requireSessionId(sessionId);
        requireOutline(sessionId);
        Map<String, Object> body = payload == null ? Map.of() : payload;
        if (!ApiSupport.bool(body.get("confirm"))) {
            throw new ApiException(409, "rebuild_requires_confirm");
        }
        BuildResult result = service.build(pathId,
                ApiSupport.payload("modules", body.get("modules")), true);
        return ApiSupport.payload("status", result.status(), "rebuilt", result.rebuilt());
    }

    /** 学习者声明掌握；前置条件失败用 <b>412</b>（全项目唯一）。 */
    @PostMapping("/paths/{pathId}/objectives/{kpId}/override")
    public Map<String, Object> override(@PathVariable String pathId,
                                        @PathVariable String kpId,
                                        @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        try {
            return service.overrideMastery(pathId, kpId, ApiSupport.str(body.get("note")));
        } catch (PreconditionFailedException e) {
            throw new ApiException(412, e.getMessage());
        }
    }

    /** 处理缺口；两类异常都映射成 422。 */
    @PostMapping("/paths/{pathId}/gaps/{gapId}/resolve")
    public Map<String, Object> resolveGap(@PathVariable String pathId,
                                          @PathVariable String gapId,
                                          @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        SourceGap gap;
        try {
            gap = service.resolveGap(pathId, gapId, ApiSupport.str(body.get("action")));
        } catch (OutlineException | PreconditionFailedException e) {
            throw new ApiException(422, e.getMessage());
        }
        return ApiSupport.payload("gap_id", gap.getId(),
                "resolution", gap.getResolution() == null ? null : gap.getResolution().value());
    }

    /** 进度：返回 {@code state["map"]} 而非整个 state。 */
    @GetMapping("/paths/{pathId}/mastery")
    public Object mastery(@PathVariable String pathId) {
        Map<String, Object> state = service.getState(pathId);
        if ("missing".equals(state.get("status"))) {
            throw new ApiException(404, "unknown path");
        }
        return state.get("map");
    }

    // -- 来源与知识库 ---------------------------------------------------------

    /**
     * 扫描文件夹并作为来源附加（参考实现 {@code attach_sources}）。
     *
     * <p>缺 {@code folder} → <b>422</b>；文件夹不存在 / 不是目录 → <b>404</b>。
     * 这个 404 映射不常见，但与参考实现一致。
     */
    @PostMapping("/paths/{pathId}/sources")
    public List<Map<String, Object>> attachSources(
            @PathVariable String pathId,
            @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        String folder = ApiSupport.str(body.get("folder"));
        if (folder.isEmpty()) {
            throw new ApiException(422, "folder required");
        }
        List<TopicSource> sources;
        try {
            sources = FolderScanner.scanFolder(Path.of(folder));
        } catch (IOException e) {
            throw new ApiException(404, e.getMessage());
        }
        return service.attachSources(pathId, sources);
    }

    /** 列出路径的来源；未知路径 → 404。 */
    @GetMapping("/paths/{pathId}/sources")
    public Object listSources(@PathVariable String pathId) {
        Map<String, Object> state = service.getState(pathId);
        if ("missing".equals(state.get("status"))) {
            throw new ApiException(404, "unknown path");
        }
        return state.get("sources");
    }

    /**
     * 设置路径的默认知识库（以 {@code knowledge_base} 来源表达）。
     *
     * <p>{@code kb_ids} 非数组 → 422；任一知识库不存在 → 404。
     */
    @PutMapping("/paths/{pathId}/knowledge-bases")
    public Map<String, Object> setPathKnowledgeBases(
            @PathVariable String pathId,
            @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        if (!(body.get("kb_ids") instanceof List<?> kbIds)) {
            throw new ApiException(422, "kb_ids must be an array");
        }
        List<TopicSource> sources = new ArrayList<>();
        int position = 0;
        for (Object rawId : kbIds) {
            String kbId = ApiSupport.str(rawId);
            Map<String, Object> kb;
            try {
                kb = knowledgeService.get(kbId);
            } catch (KnowledgeStore.UnknownKnowledgeBase e) {
                throw new ApiException(404, "unknown knowledge base: " + kbId);
            }
            TopicSource source = new TopicSource();
            source.setId("kb:" + ApiSupport.str(kb.get("id")));
            source.setKind("knowledge_base");
            source.setSourceId(ApiSupport.str(kb.get("id")));
            source.setLabel(ApiSupport.str(kb.get("name")));
            source.setExcerpt(ApiSupport.str(kb.get("description")));
            source.setPosition(position++);
            source.setAvailable("ready".equals(ApiSupport.str(kb.get("status"))));
            sources.add(source);
        }
        service.setKnowledgeBaseSources(pathId, sources);
        Map<String, Object> state = service.getState(pathId);
        return ApiSupport.payload(
                "knowledge_bases", state.getOrDefault("knowledge_bases", List.of()),
                "sources", state.get("sources"));
    }

    // -- 事件与题库 -----------------------------------------------------------

    @GetMapping("/paths/{pathId}/review-queue")
    public Object reviewQueue(@PathVariable String pathId) {
        return service.getState(pathId).get("due_reviews");
    }

    /** 事件流；不校验路径存在性（未知路径返回空列表）。 */
    @GetMapping("/paths/{pathId}/events")
    public List<Map<String, Object>> events(@PathVariable String pathId,
                                            @RequestParam(name = "since", defaultValue = "0") int since) {
        return store.eventsSince(pathId, since);
    }

    /**
     * 「建议下一问」草稿（参考实现 {@code suggest_question}）。
     *
     * <p>未知路径 → 404 {@code "unknown path"}。返回的是<b>草稿</b>，不会自动发送；
     * 无可用模型或模型报错时由 {@link TaskModel} 回退到确定性文案，接口本身不失败。
     */
    @GetMapping("/paths/{pathId}/suggest-question")
    public Map<String, Object> suggestQuestion(@PathVariable String pathId) {
        Map<String, Object> state = service.getState(pathId);
        if ("missing".equals(state.get("status"))) {
            throw new ApiException(404, "unknown path");
        }
        return ApiSupport.payload("suggestion", taskModel.suggestQuestion(state));
    }

    @GetMapping("/paths/{pathId}/questions")
    public List<QuestionBankEntry> questions(@PathVariable String pathId,
                                             @RequestParam(name = "tag", required = false) String tag) {
        return service.listQuestions(pathId, tag);
    }

    /** 整体替换题库条目标签；条目不存在 → 404。 */
    @PostMapping("/questions/{entryId}/tags")
    public QuestionBankEntry tagQuestion(@PathVariable String entryId,
                                         @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        try {
            return service.tagQuestion(entryId, stringList(body.get("tags")));
        } catch (PreconditionFailedException e) {
            throw new ApiException(404, e.getMessage());
        }
    }

    // -- 捕获与覆盖 -----------------------------------------------------------

    @GetMapping("/paths/{pathId}/proposals")
    public Object proposals(@PathVariable String pathId) {
        Map<String, Object> state = service.getState(pathId);
        if ("missing".equals(state.get("status"))) {
            throw new ApiException(404, "unknown path");
        }
        return state.get("proposals");
    }

    /** 接受或拒绝候选目标；两类异常都映射成 422。 */
    @PostMapping("/paths/{pathId}/proposals/{proposalId}/resolve")
    public Map<String, Object> resolveProposal(@PathVariable String pathId,
                                               @PathVariable String proposalId,
                                               @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        try {
            return service.resolveProposal(pathId, proposalId, ApiSupport.str(body.get("action")));
        } catch (OutlineException | PreconditionFailedException e) {
            throw new ApiException(422, e.getMessage());
        }
    }

    @GetMapping("/paths/{pathId}/coverage")
    public CoverageReport coverage(@PathVariable String pathId) {
        CoverageReport report = service.getCoverage(pathId);
        if (report == null) {
            throw new ApiException(404, "unknown path");
        }
        return report;
    }

    /**
     * 大纲质量的确定性指标（参考实现 {@code eval_outline}，{@code INV-57}）。
     *
     * <p>只读、不调模型；未知路径 → 404 {@code "unknown path"}。
     */
    @PostMapping("/paths/{pathId}/eval")
    public Map<String, Object> evalOutline(@PathVariable String pathId) {
        Map<String, Object> report = service.evalOutline(pathId);
        if (report == null) {
            throw new ApiException(404, "unknown path");
        }
        return report;
    }

    // -- 辅助 -----------------------------------------------------------------

    /**
     * {@code session_id} 是必填查询参数（参考实现 {@code Query(...)}）。
     *
     * <p>缺失时 FastAPI 返回 422，这里保持一致；不能退化成「未记录模式」，
     * 否则漏传参数会静默绕过门控。
     */
    private static void requireSessionId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new ApiException(422, "session_id required");
        }
    }

    /**
     * 地图写入的模式门控（参考实现 {@code _require_outline}）。
     *
     * <p>未记录模式 → 不门控（{@code INV-09}）；已记录且非 {@code outline} → 409。
     */
    private void requireOutline(String sessionId) {
        Object current = sessionMode(sessionId);
        if (current != null && SessionMode.normalize(current) != SessionMode.OUTLINE) {
            throw new ApiException(409, "mode_conflict: outline mode required");
        }
    }

    /** 会话模式；会话不存在时返回 {@code null}（等价于「未记录」）。 */
    private Object sessionMode(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        Map<String, Object> session = store.getSession(sessionId);
        return session == null ? null : session.get("mode");
    }

    private static boolean hasKnowledgePoints(LearningProgress progress) {
        for (LearningModule module : progress.getModules()) {
            if (!module.getKnowledgePoints().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static int objectiveCount(LearningProgress progress) {
        int total = 0;
        for (LearningModule module : progress.getModules()) {
            total += module.getKnowledgePoints().size();
        }
        return total;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> countsOf(Map<String, Object> state) {
        Object counts = state.get("counts");
        return counts instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static List<?> listOf(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    /** 把 {@code ops} 转成服务层签名；非数组时传 {@code null} 让大纲层报错。 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asOps(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        List<Map<String, Object>> ops = new ArrayList<>();
        for (Object item : list) {
            ops.add(item instanceof Map<?, ?> map ? (Map<String, Object>) map : null);
        }
        return ops;
    }

    private static List<String> stringList(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                values.add(ApiSupport.str(item));
            }
        }
        return values;
    }
}
