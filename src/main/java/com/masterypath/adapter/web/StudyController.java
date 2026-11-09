package com.masterypath.adapter.web;

import com.masterypath.adapter.tool.MasteryTools;
import com.masterypath.adapter.tool.Tool;
import com.masterypath.adapter.tool.ToolContext;
import com.masterypath.adapter.tool.ToolResult;
import com.masterypath.service.LearningService;
import com.masterypath.storage.LearningStore;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST 学习端点（参考实现 {@code _run_tool} + 四个 {@code POST}）。
 *
 * <p><b>复用工具层</b>而不是直接调服务：这样 REST 与 WS 的行为完全一致
 * （同样的模式门控、同样的 {@code metadata} 键、同样的失败文案）。
 *
 * <p>返回结构：{@code {ok: true, content: "...", ...metadata}}；
 * 工具返回 {@code ok=false} 时映射成 <b>422</b>，{@code detail} 就是工具文案。
 */
@RestController
@RequestMapping("/learning/paths/{pathId}")
public class StudyController {

    private final LearningService service;
    private final LearningStore store;

    public StudyController(LearningService service, LearningStore store) {
        this.service = service;
        this.store = store;
    }

    @PostMapping("/quiz")
    public Map<String, Object> quiz(@PathVariable String pathId,
                                    @RequestBody(required = false) Map<String, Object> payload) {
        return runTool("mastery_quiz", pathId, payload);
    }

    @PostMapping("/grade")
    public Map<String, Object> grade(@PathVariable String pathId,
                                     @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> params = payload == null ? Map.of() : payload;
        String sessionId = ApiSupport.str(params.get("session_id"));
        String turnId = ApiSupport.str(params.get("turn_id"));
        ToolContext ctx = context(pathId, sessionId);
        ctx.setTurnId(turnId);
        ToolResult result = MasteryTools.all().get("mastery_grade").execute(params, ctx);
        // 判分反馈只在 REST 题卡路径落库；WS 工具调用由 recordTurn 统一写，避免重复。
        // 只有真正拿到判分结果（graded/ungraded）才写；precondition 失败没有 metadata。
        Object status = result.metadata().get("status");
        if (!sessionId.isEmpty() && status != null
                && !"already_graded".equals(ApiSupport.str(status))) {
            service.recordGradeInteraction(pathId, sessionId,
                    ApiSupport.str(params.get("answer")), result.metadata(), turnId);
        }
        if (!result.ok()) {
            throw new ApiException(422, result.content());
        }
        return flatten(result);
    }

    @PostMapping("/assess")
    public Map<String, Object> assess(@PathVariable String pathId,
                                      @RequestBody(required = false) Map<String, Object> payload) {
        return runTool("mastery_assess", pathId, payload);
    }

    @PostMapping("/skip-question")
    public Map<String, Object> skipQuestion(@PathVariable String pathId,
                                            @RequestBody(required = false) Map<String, Object> payload) {
        return runTool("mastery_skip_question", pathId, payload);
    }

    // -- 辅助 -----------------------------------------------------------------

    /** 参考实现 {@code _run_tool}：执行工具并把结果摊平成响应体。 */
    private Map<String, Object> runTool(String toolName, String pathId,
                                        Map<String, Object> payload) {
        Map<String, Object> params = payload == null ? Map.of() : payload;
        Tool tool = MasteryTools.all().get(toolName);
        ToolResult result = tool.execute(params, context(pathId, ApiSupport.str(params.get("session_id"))));
        if (!result.ok()) {
            throw new ApiException(422, result.content());
        }
        return flatten(result);
    }

    /** 参考实现 {@code _run_tool} 的响应体：{@code ok/content} + 工具元数据。 */
    private static Map<String, Object> flatten(ToolResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("content", result.content());
        body.putAll(result.metadata());
        return body;
    }

    /**
     * 构造工具上下文。
     *
     * <p>模式取自会话行（参考实现 {@code _session_mode} 先查库，再退回内存表；
     * Java 侧模式总是先落库，因此只需查库）。会话不存在 → {@code null}，即「未记录模式」，
     * 门控放行（{@code INV-09}）。
     */
    private ToolContext context(String pathId, String sessionId) {
        ToolContext ctx = new ToolContext(service, pathId);
        ctx.setSessionId(sessionId);
        if (!sessionId.isEmpty()) {
            Map<String, Object> session = store.getSession(sessionId);
            if (session != null) {
                ctx.setSessionMode(session.get("mode"));
            }
        }
        return ctx;
    }
}
