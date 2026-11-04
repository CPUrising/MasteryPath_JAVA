package com.masterypath.adapter.tool;

import com.masterypath.service.NavigationService;

import java.util.List;
import java.util.Map;

/**
 * 只读导航工具（{@code ADR-022}）：{@code mastery_topics} / {@code mastery_sessions} /
 * {@code mastery_open_session} / {@code mastery_new_session}。
 *
 * <p>它们能浏览学习者的地图并生成交接卡，但<b>从不</b>改变路径、模式、进度或会话。
 * 是否接受卡片由前端决定。
 *
 * <p>数据构造全部委托给 {@link NavigationService}（{@code 0014} 已实现）；
 * 本类只做参数解析、校验与失败文案。
 */
public final class NavTools {

    /** 交接卡在 metadata 里的键（参考实现 {@code HANDOFF_META_KEY}）。 */
    public static final String HANDOFF_META_KEY = "mastery_handoff";

    /** 交接卡的固定说明（参考实现 {@code _HANDOFF_INSTRUCTION}）。 */
    private static final String HANDOFF_INSTRUCTION =
            "The learner now sees a card for this destination and decides whether to "
                    + "take it. Say in one short sentence what they will find there; do not "
                    + "promise to open it yourself and do not start tutoring that topic here.";

    private NavTools() {
    }

    // -- mastery_topics -------------------------------------------------------

    /** 列出课程卡片（{@code mastery_topics}）。 */
    public static final class Topics implements Tool {

        @Override
        public String name() {
            return "mastery_topics";
        }

        @Override
        public String description() {
            return "List the learner's mastery topics with their module outline, progress "
                    + "and due reviews. Use it to resolve what they call a course or goal, and "
                    + "before mastery_sessions / open_session / new_session.";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            Map<String, Object> payload = navigation(ctx).topicCards(
                    ToolSupport.stripped(params.get("query")));
            boolean empty = !(payload.get("topics") instanceof List<?> topics) || topics.isEmpty();
            payload.put("instruction", empty
                    ? "No matching mastery topic. Call mastery_topics with no query "
                    + "to see everything the learner has."
                    : "Resolve what the learner said against these ids. Use "
                    + "mastery_sessions to list conversations on one topic.");
            return ToolResult.ok(StatePayload.jsonOf(payload),
                    StatePayload.map("mastery_topics", payload));
        }
    }

    // -- mastery_sessions -----------------------------------------------------

    /** 列出某课程的会话（{@code mastery_sessions}）。 */
    public static final class Sessions implements Tool {

        @Override
        public String name() {
            return "mastery_sessions";
        }

        @Override
        public String description() {
            return "List the study conversations on one mastery topic: title, message "
                    + "count, last activity, whether a question is waiting, and the last "
                    + "message. Use it before mastery_open_session.";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            String pathId = ToolSupport.stripped(params.get("path_id"));
            Map<String, Object> topic = findTopic(ctx, pathId);
            if (topic == null) {
                return topicFailure(ctx, pathId);
            }
            Map<String, Object> payload = StatePayload.map(
                    "path_id", topic.get("path_id"), "path_name", topic.get("name"));
            payload.putAll(navigation(ctx).topicSessions(ToolSupport.str(topic.get("path_id"))));
            payload.put("instruction",
                    "Reopen one with mastery_open_session(path_id, session_id), or start "
                            + "a fresh one with mastery_new_session(path_id).");
            return ToolResult.ok(StatePayload.jsonOf(payload),
                    StatePayload.map("mastery_sessions", payload));
        }
    }

    // -- mastery_open_session -------------------------------------------------

    /** 生成「重开会话」交接卡（{@code mastery_open_session}）。 */
    public static final class OpenSession implements Tool {

        @Override
        public String name() {
            return "mastery_open_session";
        }

        @Override
        public String description() {
            return "Offer the learner a card that reopens an existing study conversation "
                    + "on a mastery topic. Validate session_id with mastery_sessions first. "
                    + "This does not navigate by itself.";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        @SuppressWarnings("unchecked")
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            String pathId = ToolSupport.stripped(params.get("path_id"));
            String sessionId = ToolSupport.stripped(params.get("session_id"));
            Map<String, Object> topic = findTopic(ctx, pathId);
            if (topic == null) {
                return topicFailure(ctx, pathId);
            }
            if (sessionId.isEmpty()) {
                return ToolResult.failure(
                        "session_id is required; call mastery_sessions for this topic's "
                                + "conversations, or mastery_new_session to start one.");
            }
            String moduleRef = ToolSupport.str(params.get("module"));
            Map<String, Object> module = navigation(ctx).resolveModule(topic, moduleRef);
            if (!moduleRef.isEmpty() && module == null) {
                return ToolResult.failure(ToolSupport.str(topic.get("name"))
                        + " has no lesson matching " + ToolSupport.repr(moduleRef) + ".");
            }
            Map<String, Object> sessions = navigation(ctx)
                    .topicSessions(ToolSupport.str(topic.get("path_id")));
            Map<String, Object> session = null;
            if (sessions.get("sessions") instanceof List<?> rows) {
                for (Object row : rows) {
                    if (row instanceof Map<?, ?> map
                            && sessionId.equals(String.valueOf(map.get("session_id")))) {
                        session = (Map<String, Object>) map;
                        break;
                    }
                }
            }
            if (session == null) {
                return ToolResult.failure(ToolSupport.str(topic.get("name"))
                        + " has no conversation " + ToolSupport.repr(sessionId)
                        + ". Call mastery_sessions for its conversations.");
            }
            Map<String, Object> payload = navigation(ctx).handoff(
                    "open", topic, module,
                    ToolSupport.stripped(params.get("opening_message")),
                    ToolSupport.stripped(params.get("reason")),
                    session);
            payload.put("instruction", HANDOFF_INSTRUCTION);
            return ToolResult.ok(StatePayload.jsonOf(payload),
                    StatePayload.map(HANDOFF_META_KEY, payload));
        }
    }

    // -- mastery_new_session --------------------------------------------------

    /** 生成「新建会话」交接卡（{@code mastery_new_session}）。 */
    public static final class NewSession implements Tool {

        @Override
        public String name() {
            return "mastery_new_session";
        }

        @Override
        public String description() {
            return "Offer the learner a card that starts a NEW study conversation on a "
                    + "mastery topic. The topic keeps all progress; only the conversation is "
                    + "new. This does not navigate by itself.";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            String pathId = ToolSupport.stripped(params.get("path_id"));
            Map<String, Object> topic = findTopic(ctx, pathId);
            if (topic == null) {
                return topicFailure(ctx, pathId);
            }
            if (ToolSupport.str(topic.get("path_id")).equals(ctx.pathId())) {
                return ToolResult.failure(
                        "This conversation is already the study session for that topic, "
                                + "so a card would only start a second one. Teach the lesson here "
                                + "instead.");
            }
            String moduleRef = ToolSupport.str(params.get("module"));
            Map<String, Object> module = navigation(ctx).resolveModule(topic, moduleRef);
            if (!moduleRef.isEmpty() && module == null) {
                return ToolResult.failure(ToolSupport.str(topic.get("name"))
                        + " has no lesson matching " + ToolSupport.repr(moduleRef) + ".");
            }
            Map<String, Object> payload = navigation(ctx).handoff(
                    "new", topic, module,
                    ToolSupport.stripped(params.get("opening_message")),
                    ToolSupport.stripped(params.get("reason")),
                    null);
            payload.put("instruction", HANDOFF_INSTRUCTION);
            return ToolResult.ok(StatePayload.jsonOf(payload),
                    StatePayload.map(HANDOFF_META_KEY, payload));
        }
    }

    // -- 辅助 -----------------------------------------------------------------

    /** 取导航服务（由上下文的服务构造，避免重复持有 store）。 */
    private static NavigationService navigation(ToolContext ctx) {
        return new NavigationService(SharedTools.service(ctx).store());
    }

    /** 解析课程；空 {@code pathId} 或空 map 返回 {@code null}。 */
    private static Map<String, Object> findTopic(ToolContext ctx, String pathId) {
        if (pathId.isEmpty()) {
            return null;
        }
        return navigation(ctx).findTopic(pathId);
    }

    /** 课程解析失败的统一文案（参考实现 {@code _topic}）。 */
    private static ToolResult topicFailure(ToolContext ctx, String pathId) {
        if (pathId.isEmpty()) {
            return ToolResult.failure(
                    "path_id is required; call mastery_topics for the ids.");
        }
        return ToolResult.failure("No mastery topic " + ToolSupport.repr(pathId)
                + " exists (or it has no map yet). "
                + "Call mastery_topics for the topics you can send the learner to.");
    }
}
