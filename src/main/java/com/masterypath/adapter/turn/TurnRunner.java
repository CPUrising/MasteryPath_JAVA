package com.masterypath.adapter.turn;

import com.masterypath.adapter.llm.ClientBuilder;
import com.masterypath.adapter.llm.LlmClient;
import com.masterypath.adapter.llm.LlmError;
import com.masterypath.adapter.loop.AgentLoop;
import com.masterypath.adapter.loop.StreamEvent;
import com.masterypath.adapter.tool.MasteryTools;
import com.masterypath.adapter.tool.StudyTools;
import com.masterypath.adapter.tool.Tool;
import com.masterypath.adapter.tool.ToolContext;
import com.masterypath.adapter.tool.ToolResult;
import com.masterypath.domain.mode.SessionMode;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.TopicSource;
import com.masterypath.service.ChatService;
import com.masterypath.service.LearningService;
import com.masterypath.storage.LearningStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * 一次回合的编排（参考实现 {@code agents/turn_runner.py}）。
 *
 * <p>本类把「请求 → 事件流」的全部逻辑收在一处，传输层（WS / SSE）只做转发：
 * <ol>
 *   <li>解析路径（会话绑定优先）与模式（先查库，再退回请求里的 {@code mode}）</li>
 *   <li>装配 {@link ToolContext}，并挂上 {@code bindMode} / {@code bindPath} 回调</li>
 *   <li>注入知识库参考块与 {@code mastery_status} 快照（快照失败必须吞掉）</li>
 *   <li>驱动 {@link AgentLoop}，逐条转发事件，并从 {@code tool_result} 派生 {@code question_card}</li>
 *   <li>模式或路径真的变了才发 {@code session_meta}（在 {@code turn_end} 之后）</li>
 *   <li>落库（best-effort）；流中断时保留已产出的正文</li>
 * </ol>
 *
 * <p><b>{@link #run} 是惰性的</b>：调用方取到 {@code text_delta} 时工具尚未执行，
 * 与参考实现的生成器语义一致，也是「逐帧推送」可被观察的前提。
 *
 * <p>本 change 只实现 {@code mastery_path}；{@code chat} 分支属 {@code 0020-chat-service}。
 */
public final class TurnRunner {

    /** 知识库参考块的检索条数（参考实现 {@code knowledge_context(top_k=4)}）。 */
    private static final int KNOWLEDGE_TOP_K = 4;

    /** 单次读取来源文件的字节上限（参考实现 {@code read_text(max_bytes=200_000)}）。 */
    private static final int READ_TEXT_MAX_BYTES = 200_000;

    /** 快照注入的固定抬头（逐字复刻参考实现）。 */
    private static final String SNAPSHOT_HEADER =
            "[Current-turn mastery_status snapshot]\n"
                    + "The runtime already read mastery_status for this turn. "
                    + "Use it directly; refresh with mastery_status after "
                    + "changing the path, mode, outline or progress.\n";

    private final LearningService learningService;
    private final LearningStore store;
    private final ClientBuilder clientBuilder;
    private final Object knowledgeService;
    private final ChatService chatService;

    /**
     * @param knowledgeService 知识库服务；{@code null} 表示没有知识库（{@code 0021} 落地真实实现）
     */
    public TurnRunner(LearningService learningService, LearningStore store,
                      ClientBuilder clientBuilder, Object knowledgeService) {
        this(learningService, store, clientBuilder, knowledgeService, null);
    }

    /**
     * @param chatService 普通聊天服务；{@code null} 表示未接入（{@code chat} 能力会明确失败）
     */
    public TurnRunner(LearningService learningService, LearningStore store,
                      ClientBuilder clientBuilder, Object knowledgeService,
                      ChatService chatService) {
        this.learningService = learningService;
        this.store = store;
        this.clientBuilder = clientBuilder;
        this.knowledgeService = knowledgeService;
        this.chatService = chatService;
    }

    /**
     * 跑一个回合，惰性产出事件。
     *
     * <p>按 {@code capability} 分派：{@code mastery_path} 走掌握式导师，其余走普通聊天
     * （参考实现 `if request.capability == "mastery_path": ... else: chat_service.stream_turn`）。
     *
     * @throws UnsupportedOperationException {@code chat} 能力但未接入 {@link ChatService}
     */
    public Iterator<TurnEvent> run(TurnRequest request) {
        if (TurnRequest.CAPABILITY_MASTERY.equals(request.capability())) {
            return new MasteryTurn(request);
        }
        if (TurnRequest.CAPABILITY_DEEP_QUESTION.equals(request.capability())) {
            return new QuizSetTurn(request, learningService, store, clientBuilder);
        }
        if (TurnRequest.CAPABILITY_CHAT.equals(request.capability())) {
            if (chatService == null) {
                throw new UnsupportedOperationException(
                        "capability '" + request.capability() + "' needs a ChatService");
            }
            return new ChatTurnAdapter(chatService.streamTurn(
                    request.sessionId(), request.message(), request.attachments()));
        }
        throw new UnsupportedOperationException(
                "unknown capability '" + request.capability() + "'");
    }

    /**
     * 把 {@link ChatService} 的扁平事件字典包成 {@link TurnEvent}。
     *
     * <p>参考实现 `kind = str(event.get("type") or "text_delta")`：缺 {@code type} 时按
     * {@code text_delta} 处理，其余键全部进 {@code data}。
     */
    private static final class ChatTurnAdapter implements Iterator<TurnEvent> {

        private final Iterator<Map<String, Object>> events;

        ChatTurnAdapter(Iterator<Map<String, Object>> events) {
            this.events = events;
        }

        @Override
        public boolean hasNext() {
            return events.hasNext();
        }

        @Override
        public TurnEvent next() {
            Map<String, Object> event = events.next();
            Object rawType = event.get("type");
            String type = rawType == null || String.valueOf(rawType).isEmpty()
                    ? TurnEvent.TEXT_DELTA : String.valueOf(rawType);
            Map<String, Object> data = new LinkedHashMap<>(event);
            data.remove("type");
            return new TurnEvent(type, data);
        }
    }

    // -- 解析辅助 -------------------------------------------------------------

    /**
     * 会话模式：先查库，再退回请求里的 {@code mode}。
     *
     * <p>返回<b>原始值</b>（可能是 {@code null}）：{@code null} 表示「未记录模式」，
     * 门控放行（{@code INV-09}）。不可在这里归一成 {@code study}。
     */
    Object sessionMode(String sessionId, String fallback) {
        Map<String, Object> session = sessionId.isEmpty() ? null : store.getSession(sessionId);
        if (session != null) {
            return session.get("mode");
        }
        SessionMode enforced = SessionMode.enforced(truthy(fallback) ? fallback : null);
        return enforced == null ? null : enforced.value();
    }

    /** 会话绑定优先；未绑定或会话不存在时退回请求里的 {@code path_id}。 */
    String resolvePathId(TurnRequest request) {
        if (!request.sessionId().isEmpty()) {
            Map<String, Object> session = store.getSession(request.sessionId());
            String bound = session == null ? "" : str(session.get("path_id")).strip();
            if (!bound.isEmpty()) {
                return bound;
            }
        }
        return request.pathId().strip();
    }

    /**
     * 会话偏好里的三态覆盖优先，否则取路径上 {@code kind == "knowledge_base"} 的来源。
     *
     * <p>三态：键不存在 → 用路径来源；键存在且为 {@code null} → 显式关闭；键存在且为列表 → 用它。
     */
    List<String> knowledgeBaseIds(TurnRequest request) {
        Map<String, Object> session = request.sessionId().isEmpty()
                ? null : store.getSession(request.sessionId());
        Object preferences = session == null ? null : session.get("preferences");
        if (preferences instanceof Map<?, ?> map && map.containsKey("knowledge_bases")
                && map.get("knowledge_bases") != null) {
            List<String> ids = new ArrayList<>();
            if (map.get("knowledge_bases") instanceof List<?> values) {
                for (Object item : values) {
                    ids.add(str(item));
                }
            }
            return ids;
        }
        LearningProgress progress = request.pathId().isEmpty() ? null : store.load(request.pathId());
        if (progress == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (TopicSource source : progress.getSources()) {
            if ("knowledge_base".equals(source.getKind())) {
                ids.add(source.getSourceId() == null || source.getSourceId().isEmpty()
                        ? source.getId() : source.getSourceId());
            }
        }
        return ids;
    }

    /**
     * {@code read_source} 用的「可读 id / 标签 → 正文」索引。
     *
     * <p>文件来源读磁盘（失败则退回 {@code excerpt}）；知识库来源优先拼装库内全部文档
     * （参考实现 {@code store.collect_documents}），失败同样退回 {@code excerpt}。
     */
    Map<String, String> sourceIndex(String pathId) {
        if (pathId.isEmpty()) {
            return Map.of();
        }
        LearningProgress progress = store.load(pathId);
        if (progress == null) {
            return Map.of();
        }
        Map<String, String> index = new LinkedHashMap<>();
        for (TopicSource source : progress.getSources()) {
            Set<String> keys = new LinkedHashSet<>();
            keys.add(source.getId());
            keys.add(source.getLabel());
            keys.add(source.getSourceId());
            String text = source.getExcerpt();
            if ("knowledge_base".equals(source.getKind())) {
                String kbId = source.getSourceId() == null || source.getSourceId().isEmpty()
                        ? source.getId() : source.getSourceId();
                List<Map.Entry<String, String>> documents = collectDocuments(kbId);
                if (!documents.isEmpty()) {
                    List<String> blocks = new ArrayList<>();
                    for (Map.Entry<String, String> document : documents) {
                        blocks.add("[" + document.getKey() + "]\n" + document.getValue());
                    }
                    text = String.join("\n\n", blocks);
                }
            } else {
                String filePath = source.getMetadata() == null
                        ? "" : str(source.getMetadata().get("path"));
                if (!filePath.isEmpty()) {
                    try {
                        text = readText(filePath);
                    } catch (IOException ignored) {
                        // 文件读不到就退回 excerpt（参考实现只捕获 OSError）。
                    }
                }
            }
            for (String key : keys) {
                if (key != null && !key.isEmpty()) {
                    index.put(key, text);
                }
            }
        }
        return index;
    }

    /** 取知识库文档；未接入知识库服务或读取失败时返回空列表。 */
    private List<Map.Entry<String, String>> collectDocuments(String kbId) {
        if (!(knowledgeService instanceof com.masterypath.service.KnowledgeService service)) {
            return List.of();
        }
        try {
            return service.store().collectDocuments(kbId);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    // -- 回合 -----------------------------------------------------------------

    /**
     * 一个 mastery 回合的惰性事件流。
     *
     * <p>用显式状态而不是「先跑完再返回列表」，是为了让 {@code text_delta} 在工具执行
     * <b>之前</b>到达调用方（参考实现用生成器达到同样效果）。
     */
    private final class MasteryTurn implements Iterator<TurnEvent> {

        private final TurnRequest request;
        private final Deque<TurnEvent> pending = new ArrayDeque<>();
        /** 已产出的事件，供落库时拼正文与取题卡。 */
        private final List<StreamEvent> produced = new ArrayList<>();

        private ToolContext ctx;
        private Iterator<StreamEvent> loop;
        private boolean initialized;
        private boolean finished;

        MasteryTurn(TurnRequest request) {
            this.request = request;
        }

        @Override
        public boolean hasNext() {
            while (pending.isEmpty() && !finished) {
                advance();
            }
            return !pending.isEmpty();
        }

        @Override
        public TurnEvent next() {
            if (!hasNext()) {
                throw new NoSuchElementException("turn already ended");
            }
            return pending.removeFirst();
        }

        /** 推进到「至少产出一个事件」或「回合结束」。 */
        private void advance() {
            if (!initialized) {
                initialized = true;
                try {
                    initialize();
                } catch (RuntimeException e) {
                    // 装配阶段失败：参考实现里异常会冒到传输层，由它兜成 internal_error。
                    pending.add(TurnEvent.error("internal_error",
                            "The turn failed: " + e.getMessage(), false));
                    finished = true;
                    return;
                }
            }
            if (loop != null) {
                try {
                    if (loop.hasNext()) {
                        StreamEvent event = loop.next();
                        produced.add(event);
                        pending.add(new TurnEvent(event.type(), event.data()));
                        if (StreamEvent.TOOL_RESULT.equals(event.type())) {
                            Object card = cardOf(event);
                            if (truthy(card)) {
                                // 派生事件：题卡不是 AgentLoop 的产出，而是 tool_result 的投影。
                                pending.add(new TurnEvent(TurnEvent.QUESTION_CARD, asMap(card)));
                            }
                        }
                        return;
                    }
                } catch (LlmError e) {
                    fail(e.code(), e.getMessage(), e.retryable());
                    return;
                } catch (RuntimeException e) {
                    // 永不静默关闭 socket。
                    fail("internal_error", "The turn failed: " + e.getMessage(), false);
                    return;
                }
                loop = null;
            }
            String finalMode = SessionMode.normalize(ctx.sessionMode()).value();
            if (ctx.modeChanged() || ctx.pathChanged()) {
                // 顺序不可调换：session_meta 必须在 turn_end 之后、落库之前。
                pending.add(TurnEvent.sessionMeta(finalMode, ctx.pathId()));
            }
            persistMasteryTurn(finalMode);
            finished = true;
        }

        /** 装配上下文、注入参考块与快照、启动回合循环。 */
        private void initialize() {
            Map<String, Object> session = request.sessionId().isEmpty()
                    ? null : store.getSession(request.sessionId());
            Object selection = request.llmSelection();
            if (!truthy(selection) && session != null
                    && session.get("preferences") instanceof Map<?, ?> preferences) {
                selection = preferences.get("llm_selection");
            }
            request.setPathId(resolvePathId(request));

            ctx = new ToolContext(learningService, request.pathId());
            ctx.setSessionMode(sessionMode(request.sessionId(), request.mode()));
            ctx.setSessionId(request.sessionId());
            ctx.setTurnId(request.turnId());
            ctx.setKnowledgeService(knowledgeService);
            List<String> kbIds = knowledgeBaseIds(request);
            ctx.setKbIds(kbIds);
            ctx.setSourceIndex(sourceIndex(request.pathId()));
            ctx.setBindMode(value -> {
                ctx.setSessionMode(value);
                ctx.setModeChanged(true);
            });
            ctx.setBindPath(value -> {
                ctx.setPathId(value);
                ctx.setPathChanged(true);
                request.setPathId(value);
                ctx.setKbIds(knowledgeBaseIds(request));
                ctx.setSourceIndex(sourceIndex(value));
            });

            String context = knowledgeContext(kbIds, request.message());
            if (!request.pathId().isEmpty()) {
                try {
                    ToolResult snapshot = MasteryTools.all().get("mastery_status")
                            .execute(Map.of(), ctx);
                    if (snapshot.ok()) {
                        context = SNAPSHOT_HEADER + snapshot.content()
                                + (context.isEmpty() ? "" : "\n\n" + context);
                    }
                } catch (RuntimeException ignored) {
                    // 状态读取失败不能拖垮整个回合。
                }
            }

            // MasteryTools.all() 已含 4 个导航工具（23 = 19 mastery + 4 nav），
            // 因此这里不需要再合并 NAV_TOOLS。
            Map<String, Tool> tools = MasteryTools.mountedTools(ctx);
            LlmClient client = clientBuilder.build(selection, request.sessionId());
            loop = new AgentLoop(tools).stream(client,
                    List.of(Map.of("role", "user", "content", request.message())),
                    ctx, null, context);
        }

        /** 错误路径：先保住已产出的内容，再发 session_meta，最后发 error。 */
        private void fail(String errorCode, String message, boolean retryable) {
            finishInterruptedTurn();
            if (ctx.modeChanged() || ctx.pathChanged()) {
                pending.add(TurnEvent.sessionMeta(
                        SessionMode.normalize(ctx.sessionMode()).value(), ctx.pathId()));
            }
            pending.add(TurnEvent.error(errorCode, message, retryable));
            finished = true;
        }

        /** 流中断时落库，但只在有可见内容时——纯错误回合不该留下空消息对。 */
        private void finishInterruptedTurn() {
            boolean hasText = false;
            boolean hasCard = false;
            for (StreamEvent event : produced) {
                if (StreamEvent.TEXT_DELTA.equals(event.type())
                        && truthy(event.data().get("text"))) {
                    hasText = true;
                }
                if (StreamEvent.TOOL_RESULT.equals(event.type()) && truthy(cardOf(event))) {
                    hasCard = true;
                }
            }
            if (hasText || hasCard) {
                persistMasteryTurn(SessionMode.normalize(ctx.sessionMode()).value());
            }
        }

        /** 落库（best-effort）：失败不能让客户端看到错误，内容已经流式送达。 */
        private void persistMasteryTurn(String finalMode) {
            if (request.sessionId().isEmpty()) {
                return;
            }
            StringBuilder text = new StringBuilder();
            Map<String, Object> card = null;
            for (StreamEvent event : produced) {
                if (StreamEvent.TEXT_DELTA.equals(event.type())) {
                    text.append(str(event.data().get("text")));
                }
                if (StreamEvent.TOOL_RESULT.equals(event.type()) && truthy(cardOf(event))) {
                    card = asMap(cardOf(event));
                }
            }
            String mode = finalMode != null ? finalMode
                    : SessionMode.normalize(sessionMode(request.sessionId(), request.mode())).value();
            try {
                learningService.recordTurn(request.pathId(), request.sessionId(),
                        request.message(), text.toString(), mode, request.turnId(), card);
            } catch (RuntimeException ignored) {
                // 持久化是 best-effort。
            }
        }
    }

    // -- 知识库参考块 ---------------------------------------------------------

    /**
     * 拼装「Reference material」块（参考实现 {@code knowledge_context}）。
     *
     * <p>检索是 best-effort：不可用或不可达的知识库被跳过，而不是让回合失败。
     *
     * @return 无命中或没有知识库时返回空串
     */
    String knowledgeContext(List<String> kbIds, String query) {
        if (knowledgeService == null || query.strip().isEmpty()) {
            return "";
        }
        List<String> blocks = new ArrayList<>();
        for (String kbId : kbIds) {
            List<?> hits;
            try {
                hits = searchKnowledge(knowledgeService, kbId, query, KNOWLEDGE_TOP_K);
            } catch (RuntimeException e) {
                hits = List.of();
            }
            if (hits.isEmpty()) {
                continue;
            }
            String name;
            try {
                Object raw = getKnowledge(knowledgeService, kbId).get("name");
                name = truthy(raw) ? str(raw) : kbId;
            } catch (RuntimeException e) {
                name = kbId;
            }
            List<String> joined = new ArrayList<>();
            for (Object hit : hits) {
                if (hit instanceof Map<?, ?> map) {
                    Object source = map.get("source");
                    joined.add("[" + (truthy(source) ? str(source) : "source") + "]\n"
                            + str(map.get("text")));
                }
            }
            blocks.add("# " + name + "\n" + String.join("\n\n", joined));
        }
        if (blocks.isEmpty()) {
            return "";
        }
        return "Reference material:\n\n" + String.join("\n\n", blocks);
    }

    /**
     * 调用知识库检索。
     *
     * <p>知识库服务在 {@code 0021} 落地；本 change 按「有 {@code search(kbId, query, topK)}
     * 方法」的鸭子类型调用，与 {@code SourceTools} 的做法一致。
     */
    private static List<?> searchKnowledge(Object service, String kbId, String query, int topK) {
        try {
            var method = service.getClass()
                    .getMethod("search", String.class, String.class, int.class);
            Object result = method.invoke(service, kbId, query, topK);
            return result instanceof List<?> list ? list : List.of();
        } catch (NoSuchMethodException e) {
            try {
                var method = service.getClass().getMethod("search", String.class, String.class);
                Object result = method.invoke(service, kbId, query);
                return result instanceof List<?> list ? list : List.of();
            } catch (ReflectiveOperationException inner) {
                throw new IllegalStateException("knowledge service has no search method", inner);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("retrieval failed", e);
        }
    }

    /** 读取知识库元信息（{@code get(kbId)}）。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> getKnowledge(Object service, String kbId) {
        try {
            Object result = service.getClass().getMethod("get", String.class).invoke(service, kbId);
            return result instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("knowledge service has no get method", e);
        }
    }

    // -- 辅助 -----------------------------------------------------------------

    /** 读取来源文件（UTF-8，无法解码的字节替换为 U+FFFD，等价 {@code errors="replace"}）。 */
    private static String readText(String filePath) throws IOException {
        byte[] data = Files.readAllBytes(Path.of(filePath));
        int limit = Math.min(data.length, READ_TEXT_MAX_BYTES);
        return new String(data, 0, limit, StandardCharsets.UTF_8);
    }

    /** 从 {@code tool_result} 的 metadata 里取题卡。 */
    private static Object cardOf(StreamEvent event) {
        Object metadata = event.data().get("metadata");
        return metadata instanceof Map<?, ?> map ? map.get(StudyTools.QUESTION_CARD_KEY) : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
    }

    /** Python 的 {@code str(x or "")}。 */
    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** Python 的真值语义：{@code null} / 空串 / 0 / 空容器为假。 */
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
