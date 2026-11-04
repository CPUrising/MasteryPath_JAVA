package com.masterypath.adapter.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 脚本化离线客户端（参考实现 {@code FakeLLMClient}）。
 *
 * <p>按顺序返回预置响应，不碰网络。{@code calls} 与 {@code toolsSeen} 记录每次调用的入参，
 * 使测试能断言「schema 真的传到了客户端」与「历史里确实写了 assistant/tool 消息」。
 *
 * <p>{@code chunkSize > 0} 时把正文切成多片，用于验证流式语义；{@code 0} 表示整段一次产出。
 */
public class FakeLlmClient implements LlmClient {

    private final List<LlmResponse> script = new ArrayList<>();
    private final int chunkSize;
    private final List<List<Map<String, Object>>> calls = new ArrayList<>();
    private final List<List<Map<String, Object>>> toolsSeen = new ArrayList<>();

    public FakeLlmClient(List<LlmResponse> script) {
        this(script, 0);
    }

    public FakeLlmClient(List<LlmResponse> script, int chunkSize) {
        if (script != null) {
            this.script.addAll(script);
        }
        this.chunkSize = Math.max(0, chunkSize);
    }

    /** 每次调用的消息列表快照（按调用顺序）。 */
    public List<List<Map<String, Object>>> calls() {
        return List.copyOf(calls);
    }

    /** 每次调用收到的工具定义快照（按调用顺序）。 */
    public List<List<Map<String, Object>>> toolsSeen() {
        return List.copyOf(toolsSeen);
    }

    @Override
    public LlmResponse chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        return next(messages, tools);
    }

    @Override
    public List<StreamChunk> streamChatEvents(List<Map<String, Object>> messages,
                                              List<Map<String, Object>> tools) {
        LlmResponse response = next(messages, tools);
        List<StreamChunk> chunks = new ArrayList<>();
        if (!response.text().isEmpty()) {
            if (chunkSize > 0) {
                String text = response.text();
                for (int start = 0; start < text.length(); start += chunkSize) {
                    chunks.add(StreamChunk.text(
                            text.substring(start, Math.min(text.length(), start + chunkSize))));
                }
            } else {
                chunks.add(StreamChunk.text(response.text()));
            }
        }
        if (!response.toolCalls().isEmpty()) {
            chunks.add(StreamChunk.calls(response.toolCalls()));
        }
        return chunks;
    }

    private LlmResponse next(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        calls.add(messages == null ? List.of() : List.copyOf(messages));
        toolsSeen.add(tools == null ? List.of() : List.copyOf(tools));
        if (script.isEmpty()) {
            return LlmResponse.text("(no more scripted responses)");
        }
        return script.remove(0);
    }
}
