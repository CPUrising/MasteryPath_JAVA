package com.masterypath.adapter.loop;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 回合循环产出的一条事件（参考实现 {@code StreamEvent}）。
 *
 * <p>事件类型与顺序是协议的一部分（{@code 0019} 的 WS 层直接转发）：
 * <ul>
 *   <li>{@link #TEXT_DELTA}：模型正文增量，{@code data.text}</li>
 *   <li>{@link #TOOL_CALL}：即将执行，{@code data.name} / {@code data.arguments}</li>
 *   <li>{@link #TOOL_RESULT}：执行完毕，{@code data.name} / {@code ok} / {@code content} / {@code metadata}</li>
 *   <li>{@link #TURN_END}：回合结束，{@code data.reason} ∈ {@code done} / {@code budget} / {@code card} / {@code max_rounds}</li>
 * </ul>
 *
 * @param type 事件类型
 * @param data 事件负载
 */
public record StreamEvent(String type, Map<String, Object> data) {

    public static final String TEXT_DELTA = "text_delta";
    public static final String TOOL_CALL = "tool_call";
    public static final String TOOL_RESULT = "tool_result";
    public static final String TURN_END = "turn_end";

    /** 回合结束原因：模型不再请求工具。 */
    public static final String REASON_DONE = "done";
    /** 回合结束原因：单回合工具调用超预算（{@code INV-60}）。 */
    public static final String REASON_BUDGET = "budget";
    /** 回合结束原因：工具出题，等学习者作答（{@code INV-44}）。 */
    public static final String REASON_CARD = "card";
    /** 回合结束原因：轮数耗尽。 */
    public static final String REASON_MAX_ROUNDS = "max_rounds";

    public StreamEvent {
        data = data == null ? Map.of() : new LinkedHashMap<>(data);
    }

    /** 正文增量事件。 */
    public static StreamEvent textDelta(String text) {
        return new StreamEvent(TEXT_DELTA, Map.of("text", text));
    }

    /** 工具调用事件。 */
    public static StreamEvent toolCall(String name, Map<String, Object> arguments) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("arguments", arguments == null ? Map.of() : arguments);
        return new StreamEvent(TOOL_CALL, data);
    }

    /** 工具结果事件。 */
    public static StreamEvent toolResult(String name, boolean ok, String content,
                                         Map<String, Object> metadata) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("ok", ok);
        data.put("content", content == null ? "" : content);
        data.put("metadata", metadata == null ? Map.of() : metadata);
        return new StreamEvent(TOOL_RESULT, data);
    }

    /** 回合结束事件。 */
    public static StreamEvent turnEnd(String reason) {
        return new StreamEvent(TURN_END, Map.of("reason", reason));
    }
}
