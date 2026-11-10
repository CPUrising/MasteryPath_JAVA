package com.masterypath.adapter.tool;

import com.masterypath.adapter.prompt.MasteryPrompts;
import com.masterypath.domain.mode.ModeGate;
import com.masterypath.domain.mode.SessionMode;
import com.masterypath.service.LearningService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 共享工具：状态快照、模式切换、路径列表 / 切换 / 离开。
 *
 * <p>这五个工具在任意模式下都放行（{@code ToolClassification.SHARED_TOOLS}），
 * 因此<b>不</b>做模式门控。
 */
public final class SharedTools {

    private SharedTools() {
    }

    // -- mastery_status -------------------------------------------------------

    /** 读取导师视角快照（{@code mastery_status}）。 */
    public static final class Status implements Tool {

        @Override
        public String name() {
            return "mastery_status";
        }

        @Override
        public String description() {
            return "Read the learner's mastery path: identity/goal, current mode, the next "
                    + "objective chosen by the hard gate, any open question, due reviews and "
                    + "the whole map with objective ids. Call this first when no current-turn "
                    + "snapshot was supplied; never guess a knowledge_point_id.";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            Map<String, Object> payload = StatePayload.of(
                    service(ctx), ctx.pathId(), ctx.sessionMode());
            return ToolResult.ok(StatePayload.json(payload),
                    StatePayload.map("mastery_status", payload));
        }
    }

    // -- mastery_mode ---------------------------------------------------------

    /** 切换会话模式（{@code mastery_mode}）。 */
    public static final class Mode implements Tool {

        @Override
        public String name() {
            return "mastery_mode";
        }

        @Override
        public String description() {
            return "Switch what this conversation is doing: 'outline' to design or change "
                    + "the outline, 'study' to work it forward, 'review' to re-test what is "
                    + "already mastered. Call it when a refusal names another mode, and tell "
                    + "the learner what you are switching to and why.";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            String requested = ToolSupport.stripped(params.get("mode")).toLowerCase();
            if (SessionMode.enforced(requested) == null) {
                StringBuilder listed = new StringBuilder();
                for (SessionMode candidate : SessionMode.values()) {
                    if (listed.length() > 0) {
                        listed.append(", ");
                    }
                    listed.append(ToolSupport.repr(candidate.value()));
                }
                return ToolResult.failure(ToolSupport.repr(requested)
                        + " is not a mode. Use one of: " + listed + ".");
            }
            LearningService service = service(ctx);
            var progress = service.store().load(ctx.pathId());
            boolean hasOutline = progress != null && progress.getModules().stream()
                    .anyMatch(module -> !module.getKnowledgePoints().isEmpty());
            String refusal = ModeGate.admissionError(requested, hasOutline);
            if (!refusal.isEmpty()) {
                return ToolResult.failure(refusal);
            }

            SessionMode previous = SessionMode.normalize(ctx.sessionMode());
            SessionMode rawPrevious = SessionMode.enforced(ctx.sessionMode());
            if (ctx.bindMode() != null) {
                ctx.bindMode().accept(requested);
            } else {
                ctx.setSessionMode(requested);
            }
            ctx.setModeChanged(ctx.modeChanged()
                    || !previous.value().equals(requested)
                    || rawPrevious == null);

            boolean persisted = true;
            String persistenceError = "";
            if (!ctx.sessionId().isEmpty()) {
                try {
                    service.setSessionMode(ctx.pathId(), ctx.sessionId(), requested);
                } catch (RuntimeException e) {
                    // 持久化失败不让工具失败：本回合已切到新模式。
                    persisted = false;
                    persistenceError = String.valueOf(e.getMessage());
                }
            }
            Map<String, Object> body = StatePayload.map(
                    "status", previous.value().equals(requested) ? "unchanged" : "switched",
                    "previous_mode", previous.value(),
                    "mode", requested,
                    "instructions", MasteryPrompts.shared().session(requested),
                    "reason", ToolSupport.truncate(
                            ToolSupport.stripped(params.get("reason")), 200),
                    "persisted", persisted,
                    "persistence_error", persistenceError);
            return ToolResult.ok(StatePayload.jsonOf(body), StatePayload.map(
                    "mode", requested,
                    "previous_mode", previous.value(),
                    "persisted", persisted));
        }
    }

    // -- mastery_paths --------------------------------------------------------

    /** 列出可切换路径（{@code mastery_paths}）。 */
    public static final class Paths implements Tool {

        @Override
        public String name() {
            return "mastery_paths";
        }

        @Override
        public String description() {
            return "List every mastery path the learner has built, with progress and due "
                    + "reviews. Use it before mastery_switch to resolve what the learner "
                    + "called a course or goal.";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            List<Map<String, Object>> paths = service(ctx).listPathOverviews();
            String active = ctx.pathId();
            Map<String, Object> body = StatePayload.map(
                    "active_path_id", active, "paths", paths);
            return ToolResult.ok(StatePayload.jsonOf(body), body);
        }
    }

    // -- mastery_switch -------------------------------------------------------

    /** 切换当前路径（{@code mastery_switch}）。 */
    public static final class Switch implements Tool {

        @Override
        public String name() {
            return "mastery_switch";
        }

        @Override
        public String description() {
            return "Point this conversation at another existing mastery path. Progress is "
                    + "kept on both paths; the conversation follows the new one from now on.";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            String requested = ToolSupport.stripped(params.get("path_id"));
            if (requested.isEmpty()) {
                return ToolResult.failure(
                        "mastery_switch needs a path_id; call mastery_paths for the ids.");
            }
            LearningService service = service(ctx);
            if (service.store().load(requested) == null) {
                return ToolResult.failure("No mastery path " + ToolSupport.repr(requested)
                        + " exists. Call mastery_paths for the ids you can switch to.");
            }
            String previous = ctx.pathId();
            if (ctx.bindPath() != null) {
                ctx.bindPath().accept(requested);
            } else {
                ctx.setPathId(requested);
            }
            service.bindSessionPath(ctx.sessionId(), requested);
            return ToolResult.ok(
                    "This conversation now follows " + ToolSupport.repr(requested)
                            + ". Call mastery_status to see where it stands.",
                    StatePayload.map(
                            "status", "switched",
                            "previous_path_id", previous,
                            "active_path_id", requested));
        }
    }

    // -- mastery_leave --------------------------------------------------------

    /** 离开当前路径，落到 scratch（{@code mastery_leave}）。 */
    public static final class Leave implements Tool {

        @Override
        public String name() {
            return "mastery_leave";
        }

        @Override
        public String description() {
            return "Stop following the current mastery path. It keeps all progress and "
                    + "can be resumed with mastery_switch; this conversation falls back to "
                    + "its own scratch path so the learner can start something else.";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            LearningService service = service(ctx);
            String sessionId = ctx.sessionId().isEmpty() ? "default" : ctx.sessionId();
            var scratch = service.ensureScratchPath(sessionId);
            String previous = ctx.pathId();
            if (ctx.bindPath() != null) {
                ctx.bindPath().accept(scratch.getPathId());
            } else {
                ctx.setPathId(scratch.getPathId());
            }
            service.bindSessionPath(ctx.sessionId(), scratch.getPathId());
            return ToolResult.ok(
                    "This conversation is now on its own scratch path. The previous "
                            + "path is untouched and resumable with mastery_switch.",
                    StatePayload.map(
                            "status", "left",
                            "previous_path_id", previous,
                            "active_path_id", scratch.getPathId()));
        }
    }

    // -- 辅助 -----------------------------------------------------------------

    /** 取上下文里的服务；缺失时抛错（工具层必须有服务）。 */
    static LearningService service(ToolContext ctx) {
        Object service = ctx.service();
        if (service instanceof LearningService learningService) {
            return learningService;
        }
        throw new IllegalStateException("tool context has no LearningService");
    }

    /** 供其他工具包复用：构造有序 map。 */
    static Map<String, Object> ordered(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }
}
