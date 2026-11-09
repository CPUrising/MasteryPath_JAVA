package com.masterypath.service;

import com.masterypath.adapter.llm.LlmClient;
import com.masterypath.adapter.llm.LlmError;
import com.masterypath.adapter.llm.ModelResolution;
import com.masterypath.adapter.llm.StreamChunk;
import com.masterypath.adapter.loop.AgentLoop;
import com.masterypath.adapter.loop.StreamEvent;
import com.masterypath.adapter.tool.MasteryTools;
import com.masterypath.adapter.tool.Tool;
import com.masterypath.adapter.tool.ToolContext;
import com.masterypath.storage.ChatStore;
import com.masterypath.storage.SettingsStore;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 普通聊天（参考实现 {@code chat/service.py}）。
 *
 * <p>与掌握式导师的三处关键差异：
 * <ul>
 *   <li>持久化在独立 {@link ChatStore}（{@code chat.db}），不是 {@code learning.db}。</li>
 *   <li><b>无模式</b>：{@link ToolContext#setSessionMode} 恒为 {@code null}，因此门控不生效。</li>
 *   <li>只挂 4 个<b>只读</b>导航工具（{@code ADR-022}），不挂 mastery 学习工具。</li>
 * </ul>
 *
 * <p>事件是<b>扁平字典</b>（{@code {type, ...}}），不是 {@code {type, data}} 嵌套——
 * 参考实现直接 {@code {"type": ..., **data}}，传输层再包成 {@code {type, data}}。
 *
 * <p><b>两条短路都在落库之前</b>：空消息与未知会话都不该在会话里留下痕迹。
 */
public class ChatService {

    /** 聊天系统提示（逐字复刻参考实现 {@code _SYSTEM}）。 */
    public static final String SYSTEM_PROMPT =
            "You are MasteryPath, a study assistant. Answer the learner directly and "
                    + "concisely in their language. When reference material is provided below, "
                    + "ground your answer in it and cite the source file name.";

    /** 导航工具回合追加的提示（逐字复刻参考实现）。 */
    static final String NAV_PROMPT_SUFFIX =
            "\n\nYou can browse the learner's mastery topics and their study "
                    + "conversations with mastery_topics / mastery_sessions. To send the "
                    + "learner to a topic, call mastery_open_session or "
                    + "mastery_new_session; the learner sees a card and decides. Never "
                    + "pretend to open or teach a topic in this chat.";

    /** 附件正文的字符上限（参考实现 {@code content[:20000]}）。 */
    private static final int ATTACHMENT_LIMIT = 20_000;

    /** 只读导航工具名（{@code NAV_TOOLS}）。 */
    private static final List<String> NAV_TOOL_NAMES = List.of(
            "mastery_topics", "mastery_sessions", "mastery_open_session", "mastery_new_session");

    private final ChatStore store;
    private final SettingsStore settings;
    private final Object knowledgeService;
    private final ClientFactory clientFactory;
    private final LearningService learningService;
    private final Map<String, Tool> navTools;

    /**
     * @param knowledgeService 知识库服务；本 change 恒为 {@code null}（检索属 {@code 0021}）
     * @param clientFactory    从解析后的配置构造客户端；{@code null} 时用 {@link ModelResolution#buildClient}
     * @param learningService  为 {@code null} 时不挂导航工具
     */
    public ChatService(ChatStore store, SettingsStore settings, Object knowledgeService,
                       ClientFactory clientFactory, LearningService learningService) {
        this.store = store;
        this.settings = settings;
        this.knowledgeService = knowledgeService;
        this.clientFactory = clientFactory == null ? ModelResolution::buildClient : clientFactory;
        this.learningService = learningService;
        this.navTools = learningService == null ? Map.<String, Tool>of() : navTools();
    }

    /** 从解析后的配置构造客户端（参考实现 {@code ClientFactory}）。 */
    @FunctionalInterface
    public interface ClientFactory {
        LlmClient create(Map<String, Object> config);
    }

    // -- 模型解析 -------------------------------------------------------------

    /** 解析当前应使用的模型配置；{@code modelId} 非空时按该模型选择。 */
    public Map<String, Object> resolveModel(String modelId) {
        Object selection = modelId == null || modelId.isEmpty()
                ? null : Map.of("model_id", modelId);
        return ModelResolution.resolveLlmConfig(settings, selection);
    }

    // -- 会话 -----------------------------------------------------------------

    public List<Map<String, Object>> listSessions() {
        return store.listSessions();
    }

    public Map<String, Object> createSession(String title, String modelId, List<String> kbIds) {
        return store.createSession(title, modelId, kbIds, Map.of());
    }

    /** 会话详情：{@code {session, messages}}。 */
    public Map<String, Object> sessionDetail(String sessionId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("session", store.getSession(sessionId));
        detail.put("messages", store.listMessages(sessionId));
        return detail;
    }

    public Map<String, Object> updateSession(String sessionId, Map<String, Object> fields) {
        return store.updateSession(sessionId, fields);
    }

    public void deleteSession(String sessionId) {
        store.deleteSession(sessionId);
    }

    /**
     * 撤回消息：删除该消息及其之后的所有消息，使对话回到发送前的状态。
     *
     * @return 被删除的消息条数
     */
    public int withdrawFrom(String sessionId, long messageId) {
        return store.deleteMessagesFrom(sessionId, messageId);
    }

    // -- 回合 -----------------------------------------------------------------

    /**
     * 跑一个聊天回合，惰性产出扁平事件字典。
     *
     * <p>事件类型：{@code text_delta} / {@code tool_call} / {@code tool_result} /
     * {@code turn_end} / {@code error}。
     */
    public Iterator<Map<String, Object>> streamTurn(String sessionId, String message,
                                                    List<Map<String, Object>> attachments) {
        return new ChatTurn(sessionId, message, attachments);
    }

    /**
     * 知识库参考块。
     *
     * <p>{@code knowledgeService} 为 {@code null}（未接入）或检索无命中时返回空串；
     * 检索失败同样返回空串——参考材料缺失不能拖垮聊天。
     */
    String knowledgeContext(List<String> kbIds, String query) {
        if (!(knowledgeService instanceof KnowledgeService service)) {
            return "";
        }
        try {
            return service.knowledgeContext(kbIds, query);
        } catch (RuntimeException e) {
            return "";
        }
    }

    // -- 回合状态机 -----------------------------------------------------------

    /**
     * 一个聊天回合的惰性事件流。
     *
     * <p>用显式状态而不是「先跑完再返回列表」，是为了让 {@code text_delta} 在工具执行
     * <b>之前</b>到达调用方（与参考实现的生成器一致）。
     */
    private final class ChatTurn implements Iterator<Map<String, Object>> {

        private final String sessionId;
        private final String message;
        private final List<Map<String, Object>> attachments;
        private final List<Map<String, Object>> pending = new ArrayList<>();
        /** 已产出的正文增量，用于落库。 */
        private final List<String> collected = new ArrayList<>();

        private boolean started;
        private boolean finished;
        private Map<String, Object> session;
        private Iterator<StreamEvent> loop;

        ChatTurn(String sessionId, String message, List<Map<String, Object>> attachments) {
            this.sessionId = sessionId;
            this.message = message == null ? "" : message;
            this.attachments = attachments == null ? List.of() : attachments;
        }

        @Override
        public boolean hasNext() {
            while (pending.isEmpty() && !finished) {
                advance();
            }
            return !pending.isEmpty();
        }

        @Override
        public Map<String, Object> next() {
            if (!hasNext()) {
                throw new NoSuchElementException("turn already ended");
            }
            return pending.remove(0);
        }

        private void advance() {
            if (!started) {
                started = true;
                if (!start()) {
                    return;
                }
            }
            if (loop != null) {
                try {
                    if (loop.hasNext()) {
                        StreamEvent event = loop.next();
                        forward(event);
                        return;
                    }
                } catch (LlmError e) {
                    fail(e.getMessage(), e.code(), e.retryable());
                    return;
                } catch (RuntimeException e) {
                    fail("The turn failed: " + e.getMessage(), "internal_error", false);
                    return;
                }
                loop = null;
            }
            finish();
        }

        /** 装配历史、解析模型并启动回合；返回 {@code false} 表示已短路结束。 */
        private boolean start() {
            String text = message.strip();
            if (text.isEmpty()) {
                pending.add(event("error", "message", "Type a message first."));
                finished = true;
                return false;
            }
            try {
                session = store.getSession(sessionId);
            } catch (ChatStore.UnknownSession e) {
                pending.add(event("error", "message", "This conversation no longer exists."));
                finished = true;
                return false;
            }

            List<Map<String, Object>> attachmentRows = new ArrayList<>();
            for (Map<String, Object> item : attachments) {
                Object name = item == null ? null : item.get("name");
                if (name != null && !String.valueOf(name).isEmpty()) {
                    attachmentRows.add(Map.of("name", String.valueOf(name)));
                }
            }
            store.addMessage(sessionId, "user", text, attachmentRows);
            if (str(session.get("title")).isEmpty()) {
                session = store.updateSession(sessionId, Map.of("title", truncate(text, 40)));
            }

            List<Map<String, Object>> history = new ArrayList<>();
            for (Map<String, Object> row : store.listMessages(sessionId)) {
                String role = str(row.get("role"));
                String content = str(row.get("content"));
                if (("user".equals(role) || "assistant".equals(role)) && !content.isEmpty()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("role", role);
                    entry.put("content", content);
                    history.add(entry);
                }
            }

            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
            String context = knowledgeContext(asStringList(session.get("kb_ids")), text);
            if (!context.isEmpty()) {
                messages.add(Map.of("role", "system", "content", context));
            }
            List<String> attachmentBlocks = new ArrayList<>();
            for (Map<String, Object> item : attachments) {
                String name = str(item == null ? null : item.get("name"));
                String body = str(item == null ? null : item.get("text")).strip();
                if (!name.isEmpty() && !body.isEmpty()) {
                    attachmentBlocks.add("[" + name + "]\n" + truncate(body, ATTACHMENT_LIMIT));
                } else if (!name.isEmpty()) {
                    attachmentBlocks.add("[" + name + "]");
                }
            }
            if (!attachmentBlocks.isEmpty()) {
                messages.add(Map.of("role", "system",
                        "content", "Attachments provided for this message:\n\n"
                                + String.join("\n\n", attachmentBlocks)));
            }
            messages.addAll(history);

            LlmClient client;
            try {
                Object selection = null;
                if (session.get("preferences") instanceof Map<?, ?> preferences) {
                    selection = preferences.get("llm_selection");
                }
                if (!truthy(selection) && truthy(session.get("model_id"))) {
                    selection = Map.of("model_id", session.get("model_id"));
                }
                Map<String, Object> config = ModelResolution.resolveLlmConfig(settings, selection);
                config.put("session_key", sessionId);
                client = clientFactory.create(config);
            } catch (LlmError e) {
                pending.add(errorEvent(e.getMessage(), e.code(), e.retryable()));
                finished = true;
                return false;
            }

            if (!navTools.isEmpty()) {
                ToolContext ctx = new ToolContext(learningService, str(session.get("path_id")));
                ctx.setSessionId(sessionId);
                ctx.setKnowledgeService(knowledgeService);
                loop = new AgentLoop(navTools).stream(client, messages, ctx,
                        SYSTEM_PROMPT + NAV_PROMPT_SUFFIX, "");
            } else {
                loop = plainStream(client, messages);
            }
            return true;
        }

        /**
         * 无导航工具时只取正文（等价参考实现的 {@code client.stream_chat}）。
         *
         * <p>惰性映射：上游产出一片正文才产出一个 {@code text_delta}，不预先收整段。
         */
        private Iterator<StreamEvent> plainStream(LlmClient client,
                                                  List<Map<String, Object>> messages) {
            Iterator<StreamChunk> chunks = client.streamChat(messages, List.of());
            return new Iterator<>() {
                private StreamEvent next;
                private boolean done;

                @Override
                public boolean hasNext() {
                    while (next == null && !done) {
                        if (!chunks.hasNext()) {
                            done = true;
                            break;
                        }
                        String text = chunks.next().text();
                        if (!text.isEmpty()) {
                            next = StreamEvent.textDelta(text);
                        }
                    }
                    return next != null;
                }

                @Override
                public StreamEvent next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException("turn already ended");
                    }
                    StreamEvent event = next;
                    next = null;
                    return event;
                }
            };
        }

        /** 转发一条 {@code AgentLoop} 事件；只透传四类，丢弃它的 {@code turn_end}。 */
        private void forward(StreamEvent event) {
            if (StreamEvent.TEXT_DELTA.equals(event.type())) {
                String text = str(event.data().get("text"));
                if (!text.isEmpty()) {
                    collected.add(text);
                    pending.add(event("text_delta", "text", text));
                }
            } else if (StreamEvent.TOOL_CALL.equals(event.type())
                    || StreamEvent.TOOL_RESULT.equals(event.type())) {
                Map<String, Object> flat = new LinkedHashMap<>();
                flat.put("type", event.type());
                flat.putAll(event.data());
                pending.add(flat);
            }
        }

        /** 错误路径：保住已产出的正文，再发 error。 */
        private void fail(String message, String errorCode, boolean retryable) {
            persistAnswer();
            pending.add(errorEvent(message, errorCode, retryable));
            finished = true;
        }

        /** 正常结束：落库正文，发带 {@code session_id} / {@code title} 的 {@code turn_end}。 */
        private void finish() {
            persistAnswer();
            Map<String, Object> current;
            try {
                current = store.getSession(sessionId);
            } catch (ChatStore.UnknownSession e) {
                current = session;
            }
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "turn_end");
            event.put("reason", "done");
            event.put("session_id", sessionId);
            event.put("title", str(current.get("title")));
            pending.add(event);
            finished = true;
        }

        /** 正文非空才落库（错误回合可能什么都没产出）。 */
        private void persistAnswer() {
            if (collected.isEmpty()) {
                return;
            }
            String answer = String.join("", collected);
            collected.clear();
            if (!answer.isEmpty()) {
                try {
                    store.addMessage(sessionId, "assistant", answer, List.of());
                } catch (RuntimeException ignored) {
                    // 落库是 best-effort：正文已经流式送达。
                }
            }
        }
    }

    // -- 辅助 -----------------------------------------------------------------

    private static Map<String, Tool> navTools() {
        Map<String, Tool> all = MasteryTools.all();
        Map<String, Tool> selected = new LinkedHashMap<>();
        for (String name : NAV_TOOL_NAMES) {
            Tool tool = all.get(name);
            if (tool != null) {
                selected.put(name, tool);
            }
        }
        return Map.copyOf(selected);
    }

    /** 单键事件（{@code {type, key: value}}）。 */
    private static Map<String, Object> event(String type, String key, Object value) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put(key, value);
        return event;
    }

    private static Map<String, Object> errorEvent(String message, String errorCode,
                                                  boolean retryable) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "error");
        event.put("message", message);
        event.put("error_code", errorCode);
        event.put("retryable", retryable);
        return event;
    }

    private static List<String> asStringList(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                values.add(str(item));
            }
        }
        return values;
    }

    private static String truncate(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0;
        }
        if (value instanceof CharSequence text) {
            return text.length() > 0;
        }
        if (value instanceof java.util.Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }
}
