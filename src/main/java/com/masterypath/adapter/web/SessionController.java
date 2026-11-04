package com.masterypath.adapter.web;

import com.masterypath.domain.mode.ModeGate;
import com.masterypath.domain.mode.SessionMode;
import com.masterypath.domain.model.LearningModule;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.service.LearningService;
import com.masterypath.storage.LearningStore;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话与模式端点（参考实现 {@code api/app.py} 470–528 行）。
 *
 * <p>模式写入的三级校验顺序不可调换（{@code design.md} 决策 8）：
 * 先校验模式值（422）→ 再查路径（404）→ 最后判准入（409）。
 */
@RestController
@RequestMapping("/learning")
public class SessionController {

    private final LearningService service;
    private final LearningStore store;

    public SessionController(LearningService service, LearningStore store) {
        this.service = service;
        this.store = store;
    }

    @GetMapping("/paths/{pathId}/sessions")
    public Map<String, Object> listSessions(@PathVariable String pathId) {
        return ApiSupport.payload("path_id", pathId, "sessions", service.listSessions(pathId));
    }

    /**
     * 会话详情：{@code {session, messages}}。
     *
     * <p>参考实现 {@code service.get_session} 返回的就是这个嵌套结构，
     * {@code test_f0_api.py#test_sessions_and_mode} 断言的也是它。
     */
    @GetMapping("/sessions/{sessionId}")
    public Map<String, Object> getSession(@PathVariable String sessionId) {
        Map<String, Object> detail = service.getSessionDetail(sessionId);
        if (detail == null) {
            throw new ApiException(404, "unknown session");
        }
        return detail;
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        if (!service.deleteSession(sessionId)) {
            throw new ApiException(404, "unknown session");
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 写入会话模式。
     *
     * <p>校验顺序：模式值 → 路径存在 → 准入。返回 {@code {session_id, mode, actor}}，
     * {@code actor} 缺省 {@code "learner"}。
     */
    @PutMapping("/paths/{pathId}/sessions/{sessionId}/mode")
    public Map<String, Object> setMode(@PathVariable String pathId,
                                       @PathVariable String sessionId,
                                       @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        String requested = ApiSupport.str(body.get("mode")).strip().toLowerCase();
        if (SessionMode.enforced(requested) == null) {
            throw new ApiException(422, "mode must be one of: " + modeList());
        }
        Map<String, Object> state = service.getState(pathId);
        if ("missing".equals(state.get("status"))) {
            throw new ApiException(404, "unknown path");
        }
        String refusal = ModeGate.admissionError(requested, hasOutline(store.load(pathId)));
        if (!refusal.isEmpty()) {
            throw new ApiException(409, refusal);
        }
        service.setSessionMode(pathId, sessionId, requested);
        Object actor = body.containsKey("actor") ? body.get("actor") : "learner";
        return ApiSupport.payload("session_id", sessionId, "mode", requested, "actor", actor);
    }

    /**
     * 写入会话偏好。
     *
     * <p>请求体有 {@code preferences} 字典就用它；否则从 {@code llm_selection} /
     * {@code knowledge_bases} 两个键组装。会话不存在时先建档再写偏好。
     */
    @PatchMapping("/paths/{pathId}/sessions/{sessionId}")
    public Map<String, Object> patchSession(@PathVariable String pathId,
                                            @PathVariable String sessionId,
                                            @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        Map<String, Object> preferences = preferencesOf(body);
        if (store.getSession(sessionId) == null) {
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("session_id", sessionId);
            created.put("path_id", pathId);
            created.put("mode", SessionMode.normalize(body.get("mode")).value());
            store.upsertSession(created);
        }
        Map<String, Object> updated = store.setSessionPreferences(sessionId, preferences);
        if (updated == null) {
            throw new ApiException(404, "unknown session");
        }
        return updated;
    }

    // -- 辅助 -----------------------------------------------------------------

    /** 参考实现 {@code ", ".join(MODES)}：按枚举声明顺序。 */
    private static String modeList() {
        StringBuilder listed = new StringBuilder();
        for (SessionMode mode : SessionMode.values()) {
            if (listed.length() > 0) {
                listed.append(", ");
            }
            listed.append(mode.value());
        }
        return listed.toString();
    }

    private static boolean hasOutline(LearningProgress progress) {
        if (progress == null) {
            return false;
        }
        for (LearningModule module : progress.getModules()) {
            if (!module.getKnowledgePoints().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** {@code preferences} 字典优先；否则取两个已知键（缺失的键不写入）。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> preferencesOf(Map<String, Object> body) {
        Object raw = body.get("preferences");
        if (raw instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        Map<String, Object> preferences = new LinkedHashMap<>();
        for (String key : List.of("llm_selection", "knowledge_bases")) {
            if (body.containsKey(key)) {
                preferences.put(key, body.get(key));
            }
        }
        return preferences;
    }
}
