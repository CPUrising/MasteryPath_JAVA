package com.masterypath.adapter.loop;

import com.masterypath.adapter.llm.LlmClient;
import com.masterypath.adapter.llm.StreamChunk;
import com.masterypath.adapter.llm.ToolCall;
import com.masterypath.adapter.tool.Tool;
import com.masterypath.adapter.tool.ToolContext;
import com.masterypath.adapter.tool.ToolDefinitions;
import com.masterypath.adapter.tool.ToolResult;
import com.masterypath.domain.mode.BehaviorBudget;
import com.masterypath.domain.mode.SessionMode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 回合循环（参考实现 {@code agents/loop.py}）。
 *
 * <p>最小循环：装配按模式框定的系统提示词，挂载全部工具，逐轮调用模型直到它不再请求工具。
 * 模式在<b>调用期</b>由工具自己执行（{@code ADR-005}），因此回合中途切模式，
 * 下一次工具调用立即生效，无需重新挂载 schema。
 *
 * <p>四种结束原因（{@link StreamEvent}）：
 * <ul>
 *   <li>{@code done}：模型不再请求工具</li>
 *   <li>{@code budget}：单回合工具调用超过 {@link BehaviorBudget#MAX_TOOL_CALLS_PER_TURN}（{@code INV-60}）</li>
 *   <li>{@code card}：工具出题并调用 {@code ctx.endTurn()}（{@code INV-44}）</li>
 *   <li>{@code max_rounds}：轮数耗尽</li>
 * </ul>
 *
 * <p>工具永不杀死回合：坏参数、未知工具、工具抛异常，都转成 {@code ok=false} 的结果回给模型。
 *
 * <p><b>{@link #stream} 是惰性的</b>：调用方取到 {@code text_delta} 时工具尚未执行，
 * 取到 {@code tool_call} 之后的下一个事件才会真正执行工具。这与参考实现的生成器语义一致，
 * 也是「先说话再动手」可被观察的前提。
 */
public final class AgentLoop {

    /** 默认最大轮数（参考实现 {@code max_rounds=6}）。 */
    public static final int DEFAULT_MAX_ROUNDS = 6;

    private final Map<String, Tool> tools;
    private final int maxRounds;

    public AgentLoop(Map<String, Tool> tools) {
        this(tools, DEFAULT_MAX_ROUNDS);
    }

    public AgentLoop(Map<String, Tool> tools, int maxRounds) {
        this.tools = tools == null ? Map.of() : new LinkedHashMap<>(tools);
        this.maxRounds = Math.max(1, maxRounds);
    }

    /**
     * 跑完整个回合，收集全部事件。
     *
     * @param client   模型客户端
     * @param messages 历史消息（不含 system）
     * @param ctx      回合上下文
     * @return 事件列表，最后一条必为 {@code turn_end}
     */
    public List<StreamEvent> run(LlmClient client, List<Map<String, Object>> messages,
                                 ToolContext ctx) {
        return run(client, messages, ctx, null, "");
    }

    /**
     * 跑完整个回合，可覆盖系统提示词并追加额外上下文。
     *
     * @param systemPrompt 覆盖默认系统提示词；{@code null} 或空串表示用默认
     * @param extraContext 追加到提示词末尾的额外上下文；空串表示不追加
     */
    public List<StreamEvent> run(LlmClient client, List<Map<String, Object>> messages,
                                 ToolContext ctx, String systemPrompt, String extraContext) {
        List<StreamEvent> events = new ArrayList<>();
        Iterator<StreamEvent> iterator = stream(client, messages, ctx, systemPrompt, extraContext);
        while (iterator.hasNext()) {
            events.add(iterator.next());
        }
        return events;
    }

    /**
     * 惰性产出事件（参考实现 {@code stream}）。
     *
     * <p>事件顺序：{@code text_delta}（可多个）→ {@code tool_call} → {@code tool_result}
     * → 下一轮 … → {@code turn_end}。
     */
    public Iterator<StreamEvent> stream(LlmClient client, List<Map<String, Object>> messages,
                                        ToolContext ctx) {
        return stream(client, messages, ctx, null, "");
    }

    /** 惰性产出事件，可覆盖系统提示词并追加额外上下文。 */
    public Iterator<StreamEvent> stream(LlmClient client, List<Map<String, Object>> messages,
                                        ToolContext ctx, String systemPrompt,
                                        String extraContext) {
        String prompt = systemPrompt == null || systemPrompt.isEmpty()
                ? systemPrompt(ctx.sessionMode()) : systemPrompt;
        if (extraContext != null && !extraContext.isEmpty()) {
            prompt = prompt + "\n\n" + extraContext;
        }
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(Map.of("role", "system", "content", prompt));
        if (messages != null) {
            history.addAll(messages);
        }
        return new EventIterator(client, history, ctx, ToolDefinitions.of(tools));
    }

    // -- 状态机 ---------------------------------------------------------------

    /** 循环的显式状态；每个状态对应「下一次 {@code next()} 该做什么」。 */
    private enum State {
        START_ROUND,
        CONSUME_CHUNKS,
        EXECUTE_CALL,
        AFTER_TOOL_CALL,
        END_CARD,
        FINISHED
    }

    /**
     * 惰性事件迭代器。
     *
     * <p>用显式状态机而不是「先跑完再返回列表」，是为了让 {@code text_delta} 在工具执行
     * <b>之前</b>到达调用方——参考实现用生成器达到同样效果。
     */
    private final class EventIterator implements Iterator<StreamEvent> {

        private final LlmClient client;
        private final List<Map<String, Object>> history;
        private final ToolContext ctx;
        private final List<Map<String, Object>> toolDefs;

        private State state = State.START_ROUND;
        private int round;
        private int callCount;
        private boolean ended;
        private StreamEvent next;

        private StringBuilder roundText = new StringBuilder();
        /** 惰性 chunk 源：取一片才会读上游下一片，保证文本增量即时透传。 */
        private Iterator<StreamChunk> chunks;
        private List<ToolCall> calls = new ArrayList<>();
        private int callIndex;
        private ToolCall pendingCall;

        EventIterator(LlmClient client, List<Map<String, Object>> history, ToolContext ctx,
                      List<Map<String, Object>> toolDefs) {
            this.client = client;
            this.history = history;
            this.ctx = ctx;
            this.toolDefs = toolDefs;
            ctx.setEndTurn(() -> ended = true);
        }

        @Override
        public boolean hasNext() {
            if (next == null) {
                next = advance();
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

        /** 推进状态机直到产出一个事件；返回 {@code null} 表示回合结束。 */
        private StreamEvent advance() {
            while (true) {
                switch (state) {
                    case START_ROUND -> {
                        if (round >= maxRounds) {
                            state = State.FINISHED;
                            return StreamEvent.turnEnd(StreamEvent.REASON_MAX_ROUNDS);
                        }
                        roundText = new StringBuilder();
                        calls = new ArrayList<>();
                        callIndex = 0;
                        chunks = client.streamChat(history, toolDefs);
                        state = State.CONSUME_CHUNKS;
                    }
                    case CONSUME_CHUNKS -> {
                        if (chunks.hasNext()) {
                            StreamChunk chunk = chunks.next();
                            if (!chunk.toolCalls().isEmpty()) {
                                calls.addAll(chunk.toolCalls());
                            }
                            if (!chunk.text().isEmpty()) {
                                roundText.append(chunk.text());
                                return StreamEvent.textDelta(chunk.text());
                            }
                            continue;
                        }
                        if (calls.isEmpty()) {
                            state = State.FINISHED;
                            return StreamEvent.turnEnd(StreamEvent.REASON_DONE);
                        }
                        // OpenAI 要求「请求工具调用的 assistant 消息」在 tool 结果之前存在。
                        history.add(assistantMessage(roundText.toString(), calls));
                        state = State.EXECUTE_CALL;
                    }
                    case EXECUTE_CALL -> {
                        if (callIndex >= calls.size()) {
                            round++;
                            state = State.START_ROUND;
                            continue;
                        }
                        ToolCall call = calls.get(callIndex++);
                        callCount++;
                        if (callCount > BehaviorBudget.MAX_TOOL_CALLS_PER_TURN) {
                            // 行为预算（INV-60）：本回合不再发起调用，且不执行这一次。
                            state = State.FINISHED;
                            return StreamEvent.turnEnd(StreamEvent.REASON_BUDGET);
                        }
                        pendingCall = call;
                        state = State.AFTER_TOOL_CALL;
                        return StreamEvent.toolCall(call.name(), call.arguments());
                    }
                    case AFTER_TOOL_CALL -> {
                        ToolResult result = execute(pendingCall);
                        history.add(toolMessage(pendingCall.id(), result.content()));
                        // 出题即结束回合（INV-44）：先交付 tool_result，再以 card 结束。
                        state = ended ? State.END_CARD : State.EXECUTE_CALL;
                        return StreamEvent.toolResult(pendingCall.name(), result.ok(),
                                result.content(), result.metadata());
                    }
                    case END_CARD -> {
                        state = State.FINISHED;
                        return StreamEvent.turnEnd(StreamEvent.REASON_CARD);
                    }
                    case FINISHED -> {
                        return null;
                    }
                    default -> {
                        return null;
                    }
                }
            }
        }

        /** 执行一次工具调用，把三种失败都转成 {@code ok=false}。 */
        private ToolResult execute(ToolCall call) {
            if (call.parseError()) {
                // 供应商给了坏参数：回一个失败结果让模型自我纠正，而不是崩掉。
                return ToolResult.failure("invalid tool arguments");
            }
            Tool tool = tools.get(call.name());
            if (tool == null) {
                return ToolResult.failure("unknown tool: " + call.name());
            }
            try {
                return tool.execute(call.arguments(), ctx);
            } catch (RuntimeException e) {
                // 工具 MUST NOT 杀死回合。
                return ToolResult.failure("tool failed: " + e.getMessage());
            }
        }
    }

    // -- 辅助 -----------------------------------------------------------------

    /** 默认系统提示词（参考实现 {@code _system_prompt}，逐字复刻）。 */
    static String systemPrompt(Object modeValue) {
        SessionMode current = SessionMode.normalize(modeValue);
        return "You are the MasteryPath tutor. Decide what to teach and ask; a "
                + "deterministic engine decides whether the learner may advance. "
                + "Current mode: " + current.value() + ". " + current.instruction() + " "
                + "Begin each turn with the mastery_status snapshot supplied by the "
                + "runtime, or call mastery_status first; never guess a knowledge_point_id "
                + "or the next objective. Trust the engine's next objective and use "
                + "mastery_mode when the learner asks for something this mode cannot do.";
    }

    /** assistant 消息：正文为空时写 {@code null}（OpenAI 的合法写法）。 */
    private static Map<String, Object> assistantMessage(String text, List<ToolCall> calls) {
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (ToolCall call : calls) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", call.name());
            function.put("arguments", writeJson(call.arguments()));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", call.id());
            entry.put("type", "function");
            entry.put("function", function);
            serialized.add(entry);
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", text.isEmpty() ? null : text);
        message.put("tool_calls", serialized);
        return message;
    }

    private static Map<String, Object> toolMessage(String callId, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", callId);
        message.put("content", content);
        return message;
    }

    private static String writeJson(Map<String, Object> arguments) {
        try {
            return com.masterypath.domain.model.JsonSupport.objectMapper()
                    .writeValueAsString(arguments == null ? Map.of() : arguments);
        } catch (Exception e) {
            return "{}";
        }
    }
}
