package com.masterypath.adapter.llm;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 模型访问的唯一抽象（参考实现 {@code LLMClient} 协议，{@code ADR-014}）。
 *
 * <p>domain 层永不 import 供应商 SDK；回合循环只依赖本接口。
 * 测试用 {@link FakeLlmClient} 离线跑，真实供应商只在 {@link OpenAiCompatibleClient} 里出现。
 */
public interface LlmClient {

    /**
     * 非流式补全。
     *
     * @param messages OpenAI 格式的消息列表
     * @param tools    OpenAI 格式的工具定义；空列表表示不带工具
     * @return 正文与工具调用
     * @throws LlmError 供应商失败（消息安全可展示）
     */
    LlmResponse chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools);

    /**
     * 流式补全（惰性）：调用方取到一片才会去读下一片。
     *
     * <p>默认实现回退到 {@link #streamChatEvents}（因此也会回退到 {@link #chat}），
     * 这样「只实现 {@code chat} 的客户端」也能被回合循环使用。
     *
     * <p>真正的流式客户端（如 {@link OpenAiCompatibleClient}）必须覆盖本方法，
     * 否则上游增量会被攒成一次响应，前端就看不到逐字输出。
     *
     * @param messages OpenAI 格式的消息列表
     * @param tools    OpenAI 格式的工具定义
     * @return 惰性文本片迭代器；工具调用片（若有）在末尾
     * @throws LlmError 供应商失败（首片或任意片产出时抛出）
     */
    default Iterator<StreamChunk> streamChat(List<Map<String, Object>> messages,
                                             List<Map<String, Object>> tools) {
        return streamChatEvents(messages, tools).iterator();
    }

    /**
     * 流式补全（一次性收集）。
     *
     * <p>保留给测试与「只需要完整结果」的调用方；回合链路应使用 {@link #streamChat}。
     * 默认实现回退到 {@link #chat}，把整段正文作为一个文本片产出。
     *
     * @param messages OpenAI 格式的消息列表
     * @param tools    OpenAI 格式的工具定义
     * @return 文本片与（可选的）工具调用片
     * @throws LlmError 供应商失败
     */
    default List<StreamChunk> streamChatEvents(List<Map<String, Object>> messages,
                                               List<Map<String, Object>> tools) {
        LlmResponse response = chat(messages, tools);
        List<StreamChunk> chunks = new java.util.ArrayList<>();
        if (!response.text().isEmpty()) {
            chunks.add(StreamChunk.text(response.text()));
        }
        if (!response.toolCalls().isEmpty()) {
            chunks.add(StreamChunk.calls(response.toolCalls()));
        }
        return chunks;
    }
}
