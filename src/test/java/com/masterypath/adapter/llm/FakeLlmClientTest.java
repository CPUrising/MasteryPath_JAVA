package com.masterypath.adapter.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 脚本化离线客户端（参考实现 {@code FakeLLMClient}）。
 *
 * <p>它是全部回合测试的基础设施，因此单独验证：脚本顺序、切分、入参记录、默认流式回退。
 */
class FakeLlmClientTest {

    @Test
    @DisplayName("按脚本顺序返回，耗尽后给出提示文本")
    void returnsScriptInOrder() {
        FakeLlmClient client = new FakeLlmClient(List.of(
                LlmResponse.text("first"), LlmResponse.text("second")));

        assertThat(client.chat(messages(), List.of()).text()).isEqualTo("first");
        assertThat(client.chat(messages(), List.of()).text()).isEqualTo("second");
        assertThat(client.chat(messages(), List.of()).text())
                .isEqualTo("(no more scripted responses)");
    }

    @Test
    @DisplayName("记录每次调用的消息与工具定义")
    void recordsCallsAndTools() {
        FakeLlmClient client = new FakeLlmClient(List.of(LlmResponse.text("ok")));
        List<Map<String, Object>> tools = List.of(Map.of("type", "function"));

        client.chat(messages(), tools);

        assertThat(client.calls()).hasSize(1);
        assertThat(client.calls().get(0)).containsExactly(Map.of("role", "user", "content", "hi"));
        assertThat(client.toolsSeen()).hasSize(1);
        assertThat(client.toolsSeen().get(0)).isEqualTo(tools);
    }

    @Test
    @DisplayName("chunkSize=0 时整段一次产出")
    void singleChunkWhenChunkSizeZero() {
        FakeLlmClient client = new FakeLlmClient(List.of(LlmResponse.text("hello")));

        List<StreamChunk> chunks = client.streamChatEvents(messages(), List.of());

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo("hello");
    }

    @Test
    @DisplayName("chunkSize>0 时按字符数切分")
    void splitsByChunkSize() {
        FakeLlmClient client = new FakeLlmClient(List.of(LlmResponse.text("Let me check.")), 4);

        List<StreamChunk> chunks = client.streamChatEvents(messages(), List.of());

        assertThat(chunks).extracting(StreamChunk::text)
                .containsExactly("Let ", "me c", "heck", ".");
    }

    @Test
    @DisplayName("工具调用在文本之后单独一个 chunk")
    void toolCallsComeAfterText() {
        FakeLlmClient client = new FakeLlmClient(List.of(
                new LlmResponse("lead-in", List.of(ToolCall.of("mastery_status")))));

        List<StreamChunk> chunks = client.streamChatEvents(messages(), List.of());

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).text()).isEqualTo("lead-in");
        assertThat(chunks.get(1).toolCalls()).extracting(ToolCall::name)
                .containsExactly("mastery_status");
    }

    @Test
    @DisplayName("只实现 chat 的客户端走默认流式回退")
    void defaultStreamFallsBackToChat() {
        LlmClient plain = (messages, tools) -> LlmResponse.text("plain answer");

        List<StreamChunk> chunks = plain.streamChatEvents(messages(), List.of());

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo("plain answer");
    }

    @Test
    @DisplayName("默认流式回退也产出工具调用片")
    void defaultStreamEmitsCalls() {
        LlmClient plain = (messages, tools) -> LlmResponse.calls(ToolCall.of("mastery_status"));

        List<StreamChunk> chunks = plain.streamChatEvents(messages(), List.of());

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).toolCalls()).hasSize(1);
    }

    private static List<Map<String, Object>> messages() {
        return List.of(Map.of("role", "user", "content", "hi"));
    }
}
