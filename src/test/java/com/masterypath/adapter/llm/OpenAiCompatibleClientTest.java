package com.masterypath.adapter.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenAI 兼容客户端（参考实现 {@code OpenAICompatibleClient}）。
 *
 * <p>用 JDK 自带 {@link HttpServer} 起一个本地端点，验证真实 HTTP 行为——
 * 不 mock 传输层，因此请求头、请求体、状态码映射都被真实执行。
 */
class OpenAiCompatibleClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastBody = new AtomicReference<>("");
    private final AtomicReference<Map<String, List<String>>> lastHeaders = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = "{}";
    private volatile List<String> streamLines = List.of();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Map<String, List<String>> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((name, values) ->
                    headers.put(name.toLowerCase(), new ArrayList<>(values)));
            lastHeaders.set(headers);
            byte[] payload;
            if (status == 200 && !streamLines.isEmpty()) {
                payload = String.join("\n", streamLines).getBytes(StandardCharsets.UTF_8);
            } else {
                payload = responseBody.getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().add("content-type",
                    streamLines.isEmpty() ? "application/json" : "text/event-stream");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // -- 非流式 ---------------------------------------------------------------

    @Test
    @DisplayName("chat 发送 tools 与 tool_choice=auto，并解析工具调用")
    void chatSendsToolsAndParsesCalls() {
        responseBody = """
                {"choices":[{"message":{"content":"","tool_calls":[
                  {"id":"call-1","type":"function",
                   "function":{"name":"mastery_quiz","arguments":"{\\"knowledge_point_id\\": \\"k1\\"}"}}]}}]}
                """;

        LlmResponse response = client().chat(
                List.of(Map.of("role", "user", "content", "hi")), tools());

        assertThat(lastBody.get()).contains("\"tool_choice\":\"auto\"")
                .contains("\"mastery_quiz\"")
                .contains("\"stream\":false");
        assertThat(response.toolCalls()).hasSize(1);
        ToolCall call = response.toolCalls().get(0);
        assertThat(call.name()).isEqualTo("mastery_quiz");
        assertThat(call.id()).isEqualTo("call-1");
        assertThat(call.arguments()).containsEntry("knowledge_point_id", "k1");
        assertThat(call.parseError()).isFalse();
    }

    @Test
    @DisplayName("chat 把非法参数标记为 parseError")
    void chatMarksInvalidArguments() {
        responseBody = """
                {"choices":[{"message":{"content":null,"tool_calls":[
                  {"id":"c","type":"function",
                   "function":{"name":"mastery_grade","arguments":"not json"}}]}}]}
                """;

        ToolCall call = client().chat(messages(), tools()).toolCalls().get(0);

        assertThat(call.parseError()).isTrue();
        assertThat(call.arguments()).isEmpty();
    }

    @Test
    @DisplayName("chat 跳过没有名字的工具调用")
    void chatSkipsNamelessCalls() {
        responseBody = """
                {"choices":[{"message":{"content":"hi","tool_calls":[
                  {"id":"c","type":"function","function":{"arguments":"{}"}}]}}]}
                """;

        LlmResponse response = client().chat(messages(), tools());

        assertThat(response.text()).isEqualTo("hi");
        assertThat(response.toolCalls()).isEmpty();
    }

    @Test
    @DisplayName("不带工具时不发送 tools 字段")
    void chatOmitsToolsWhenEmpty() {
        responseBody = "{\"choices\":[{\"message\":{\"content\":\"plain\"}}]}";

        LlmResponse response = client().chat(messages(), List.of());

        assertThat(lastBody.get()).doesNotContain("\"tools\"");
        assertThat(response.text()).isEqualTo("plain");
    }

    // -- 错误映射 -------------------------------------------------------------

    @Test
    @DisplayName("401 映射为 auth_error，且消息不含上游原文")
    void authErrorIsStructuredAndSafe() {
        status = 401;
        responseBody = "upstream-secret-token";

        assertThatThrownBy(() -> client().chat(messages(), List.of()))
                .isInstanceOf(LlmError.class)
                .satisfies(error -> {
                    LlmError llmError = (LlmError) error;
                    assertThat(llmError.code()).isEqualTo("auth_error");
                    assertThat(llmError.retryable()).isFalse();
                    assertThat(llmError.getMessage()).doesNotContain("upstream-secret-token");
                });
    }

    @Test
    @DisplayName("404 / 429 / 400 / 5xx / 其他 的 code 与 retryable")
    void httpErrorBranches() {
        assertThat(LlmError.httpError(404).code()).isEqualTo("model_not_found");
        assertThat(LlmError.httpError(404).retryable()).isFalse();

        assertThat(LlmError.httpError(429).code()).isEqualTo("rate_limited");
        assertThat(LlmError.httpError(429).retryable()).isTrue();

        assertThat(LlmError.httpError(400).code()).isEqualTo("bad_request");
        assertThat(LlmError.httpError(400).retryable()).isFalse();

        assertThat(LlmError.httpError(503).code()).isEqualTo("upstream_error");
        assertThat(LlmError.httpError(503).retryable()).isTrue();

        assertThat(LlmError.httpError(418).code()).isEqualTo("upstream_error");
        assertThat(LlmError.httpError(418).retryable()).isFalse();
        assertThat(LlmError.httpError(418).getMessage()).contains("418");
    }

    @Test
    @DisplayName("未配置 base_url 或 model 时拒绝调用")
    void requiresConfiguration() {
        assertThatThrownBy(() -> new OpenAiCompatibleClient("", "k", "m").chat(messages(), List.of()))
                .isInstanceOf(LlmError.class)
                .hasMessageContaining("No chat model is configured");
        assertThatThrownBy(() -> new OpenAiCompatibleClient(baseUrl, "k", "").chat(messages(), List.of()))
                .isInstanceOf(LlmError.class)
                .hasMessageContaining("No chat model is configured");
    }

    @Test
    @DisplayName("anthropic 协议明确拒绝")
    void anthropicProtocolRefused() {
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                baseUrl, "k", "m", "anthropic", null, Map.of(), "", null);

        assertThatThrownBy(() -> client.chat(messages(), List.of()))
                .isInstanceOf(LlmError.class)
                .hasMessageContaining("Anthropic chat is not supported yet");
    }

    // -- 请求头 ---------------------------------------------------------------

    @Test
    @DisplayName("opencode 网关注入 x-opencode-session，其他网关不注入")
    void opencodeSessionHeader() {
        OpenAiCompatibleClient opencode = new OpenAiCompatibleClient(
                "https://opencode.ai/zen/go/v1", "k", "m", "openai", null, Map.of(), "session-1", null);
        assertThat(opencode.headers()).containsEntry("x-opencode-session", "session-1");

        OpenAiCompatibleClient deepseek = new OpenAiCompatibleClient(
                "https://api.deepseek.com", "k", "m");
        assertThat(deepseek.headers()).doesNotContainKey("x-opencode-session");
    }

    @Test
    @DisplayName("显式 extraHeaders 优先于自动注入")
    void explicitHeadersWin() {
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                "https://opencode.ai/zen/go/v1", "k", "m", "openai", null,
                Map.of("x-opencode-session", "explicit", "x-other", "1"), "ignored", null);

        Map<String, String> headers = client.headers();

        assertThat(headers).containsEntry("x-opencode-session", "explicit")
                .containsEntry("x-other", "1");
    }

    @Test
    @DisplayName("有 api_key 时带 Bearer 头，无 key 时不带")
    void bearerHeaderOnlyWithKey() {
        assertThat(new OpenAiCompatibleClient(baseUrl, "secret", "m").headers())
                .containsEntry("authorization", "Bearer secret");
        assertThat(new OpenAiCompatibleClient(baseUrl, "", "m").headers())
                .doesNotContainKey("authorization");
    }

    @Test
    @DisplayName("chatEndpoint 对已含 /chat/completions 的 base_url 不重复拼接")
    void chatEndpointIdempotent() {
        assertThat(OpenAiCompatibleClient.chatEndpoint("https://x/v1"))
                .isEqualTo("https://x/v1/chat/completions");
        assertThat(OpenAiCompatibleClient.chatEndpoint("https://x/v1/"))
                .isEqualTo("https://x/v1/chat/completions");
        assertThat(OpenAiCompatibleClient.chatEndpoint("https://x/v1/chat/completions"))
                .isEqualTo("https://x/v1/chat/completions");
    }

    // -- 流式 -----------------------------------------------------------------

    @Test
    @DisplayName("streamChatEvents 发送 stream=true 与 tools，并解析文本与工具调用")
    void streamSendsStreamFlagAndParses() {
        streamLines = List.of(
                "data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}",
                "",
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call-1\","
                        + "\"function\":{\"name\":\"mastery_status\",\"arguments\":\"{}\"}}]}}]}",
                "",
                "data: [DONE]",
                "");

        List<StreamChunk> chunks = client().streamChatEvents(messages(), tools());

        assertThat(lastBody.get()).contains("\"stream\":true").contains("\"tool_choice\":\"auto\"");
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).text()).isEqualTo("hi");
        assertThat(chunks.get(1).toolCalls().get(0).name()).isEqualTo("mastery_status");
    }

    @Test
    @DisplayName("streamChat 惰性：上游未结束时也能取到第一片")
    void streamChatIsLazy() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        HttpServer lazyServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        lazyServer.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("content-type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(("data: {\"choices\":[{\"delta\":{\"content\":\"first\"}}]}\n\n")
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();
                // 等到消费方取走第一片后才继续写：攒批实现会在这里卡住直到超时。
                release.await(5, TimeUnit.SECONDS);
                out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        lazyServer.start();
        try {
            String url = "http://127.0.0.1:" + lazyServer.getAddress().getPort() + "/v1";
            Iterator<StreamChunk> chunks =
                    new OpenAiCompatibleClient(url, "k", "m").streamChat(messages(), List.of());

            long startedAt = System.nanoTime();
            assertThat(chunks.hasNext()).isTrue();
            StreamChunk first = chunks.next();
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

            assertThat(first.text()).isEqualTo("first");
            assertThat(elapsedMs).isLessThan(2_000);
            release.countDown();
            assertThat(chunks.hasNext()).isFalse();
        } finally {
            release.countDown();
            lazyServer.stop(0);
        }
    }

    @Test
    @DisplayName("流式请求的非 200 也映射成结构化错误")
    void streamErrorIsStructured() {
        status = 429;
        responseBody = "slow down";

        assertThatThrownBy(() -> client().streamChatEvents(messages(), List.of()))
                .isInstanceOf(LlmError.class)
                .satisfies(error -> assertThat(((LlmError) error).code()).isEqualTo("rate_limited"));
    }

    // -- 辅助 -----------------------------------------------------------------

    private OpenAiCompatibleClient client() {
        return new OpenAiCompatibleClient(baseUrl, "k", "m");
    }

    private static List<Map<String, Object>> messages() {
        return List.of(Map.of("role", "user", "content", "hi"));
    }

    private static List<Map<String, Object>> tools() {
        return List.of(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "mastery_quiz",
                        "parameters", Map.of("type", "object"))));
    }
}
