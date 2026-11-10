package com.masterypath.adapter.tool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次工具调用的结果（参考实现 {@code ToolResult}）。
 *
 * <p>{@code ok=false} 不是异常：它表示「工具拒绝或失败」，内容会作为 tool 消息回给模型，
 * 让模型自我纠正。回合循环因此永不因工具失败而中断。
 *
 * @param ok       是否成功
 * @param content  回给模型的文本
 * @param metadata 结构化附加信息（前端卡片、事件等）
 */
public record ToolResult(boolean ok, String content, Map<String, Object> metadata) {

    public ToolResult {
        content = content == null ? "" : content;
        metadata = metadata == null ? Map.of() : new LinkedHashMap<>(metadata);
    }

    /** 成功结果。 */
    public static ToolResult ok(String content) {
        return new ToolResult(true, content, Map.of());
    }

    /** 成功结果 + 元数据。 */
    public static ToolResult ok(String content, Map<String, Object> metadata) {
        return new ToolResult(true, content, metadata);
    }

    /** 失败结果。 */
    public static ToolResult failure(String content) {
        return new ToolResult(false, content, Map.of());
    }

    /** 失败结果 + 元数据（例如 {@code ungraded} 的原因与判分结果）。 */
    public static ToolResult failure(String content, Map<String, Object> metadata) {
        return new ToolResult(false, content, metadata);
    }
}
