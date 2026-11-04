package com.masterypath.adapter.llm;

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
     * 流式补全：文本片随产出即时到达，工具调用在流末尾一次性到达。
     *
     * <p>默认实现回退到 {@link #chat}，把整段正文作为一个文本片产出。
     * 这样「只实现 {@code chat} 的客户端」也能被回合循环使用
     * （参考实现 {@code loop.py:98-105} 有同样的回退）。
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
