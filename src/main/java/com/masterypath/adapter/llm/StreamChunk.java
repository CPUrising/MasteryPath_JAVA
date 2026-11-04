package com.masterypath.adapter.llm;

import java.util.List;

/**
 * 流式补全的一片（参考实现 {@code StreamChunk}）。
 *
 * <p>文本片随供应商产出即时到达；工具调用由适配器拼装后<b>在流末尾一次性</b>交付。
 * 因此一个 chunk 要么带文本、要么带工具调用，通常不会同时带两者。
 *
 * @param text      文本增量，可能为空串
 * @param toolCalls 拼装完成的工具调用，通常只在最后一个 chunk 出现
 */
public record StreamChunk(String text, List<ToolCall> toolCalls) {

    public StreamChunk {
        text = text == null ? "" : text;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /** 文本增量片。 */
    public static StreamChunk text(String text) {
        return new StreamChunk(text, List.of());
    }

    /** 工具调用片（流末尾）。 */
    public static StreamChunk calls(List<ToolCall> calls) {
        return new StreamChunk("", calls);
    }
}
