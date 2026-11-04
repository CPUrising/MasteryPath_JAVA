package com.masterypath.adapter.llm;

import java.util.List;

/**
 * 一次非流式补全的结果（参考实现 {@code LLMResponse}）。
 *
 * @param text      模型正文，可能为空串
 * @param toolCalls 模型请求的工具调用，可能为空列表
 */
public record LlmResponse(String text, List<ToolCall> toolCalls) {

    public LlmResponse {
        text = text == null ? "" : text;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /** 纯文本响应。 */
    public static LlmResponse text(String text) {
        return new LlmResponse(text, List.of());
    }

    /** 纯工具调用响应（无正文）。 */
    public static LlmResponse calls(ToolCall... calls) {
        return new LlmResponse("", List.of(calls));
    }
}
