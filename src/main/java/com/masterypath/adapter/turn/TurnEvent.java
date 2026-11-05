package com.masterypath.adapter.turn;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 传输层事件（参考实现 {@code TurnEvent}）。
 *
 * <p>WS 端点把它序列化成 {@code {"type": ..., "data": {...}}} 逐条推送。
 *
 * <p>事件词汇表（与 {@code AgentLoop} 的 {@code StreamEvent} 相同，另加两个派生事件）：
 * <ul>
 *   <li>{@code text_delta} / {@code tool_call} / {@code tool_result} / {@code turn_end}：直接转发</li>
 *   <li>{@link #QUESTION_CARD}：从 {@code tool_result.metadata.mastery_question_card} 派生</li>
 *   <li>{@link #SESSION_META}：模式或路径变化时发射</li>
 *   <li>{@link #ERROR}：{@code {error_code, message, retryable}}</li>
 * </ul>
 *
 * @param type 事件类型
 * @param data 事件负载
 */
public record TurnEvent(String type, Map<String, Object> data) {

    public static final String TEXT_DELTA = "text_delta";
    public static final String TOOL_CALL = "tool_call";
    public static final String TOOL_RESULT = "tool_result";
    public static final String TURN_END = "turn_end";
    public static final String QUESTION_CARD = "question_card";
    public static final String SESSION_META = "session_meta";
    public static final String ERROR = "error";

    public TurnEvent {
        data = data == null ? Map.of() : new LinkedHashMap<>(data);
    }

    /** 错误事件；{@code retryable} 决定前端是否显示「重试」。 */
    public static TurnEvent error(String errorCode, String message, boolean retryable) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("error_code", errorCode);
        data.put("message", message);
        data.put("retryable", retryable);
        return new TurnEvent(ERROR, data);
    }

    /** 会话元信息；前端据此更新侧栏的模式与路径。 */
    public static TurnEvent sessionMeta(String mode, String pathId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mastery_session_mode", mode);
        data.put("mastery_path_id", pathId);
        return new TurnEvent(SESSION_META, data);
    }
}
