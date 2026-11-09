package com.masterypath.adapter.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 最小 OpenAI 兼容客户端（参考实现 {@code OpenAICompatibleClient}）。
 *
 * <p>兼容 OpenAI、DeepSeek 与 Ollama 的 OpenAI 兼容端点。三条硬性约束：
 * <ol>
 *   <li><b>API Key 不出本对象</b>：只进请求头，不进日志、不进异常消息。</li>
 *   <li><b>上游响应体不回显</b>：非 200 一律映射成 {@link LlmError#httpError(int)}。</li>
 *   <li><b>超时与网络错误结构化</b>：都映射成 {@code upstream_error}，超时标记可重试。</li>
 * </ol>
 *
 * <p>参考实现的 {@code DeepSeekClient} 刻意不移植：它自己标注「仅为向后兼容，
 * 新代码用 {@code OpenAICompatibleClient}」，而 Java 侧没有历史包袱。
 */
public class OpenAiCompatibleClient implements LlmClient {

    private static final String CHAT_COMPLETIONS = "/chat/completions";

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String protocol;
    private final Duration timeout;
    private final Map<String, String> extraHeaders;
    private final String sessionKey;
    private final HttpClient http;
    private final ObjectMapper mapper = JsonSupport.objectMapper();

    public OpenAiCompatibleClient(String baseUrl, String apiKey, String model) {
        this(baseUrl, apiKey, model, "openai", Duration.ofSeconds(120), Map.of(), "", null);
    }

    public OpenAiCompatibleClient(String baseUrl, String apiKey, String model, String protocol,
                                  Duration timeout, Map<String, String> extraHeaders,
                                  String sessionKey, HttpClient httpClient) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.strip();
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model == null ? "" : model;
        this.protocol = (protocol == null ? "openai" : protocol).toLowerCase(Locale.ROOT);
        this.timeout = timeout == null ? Duration.ofSeconds(120) : timeout;
        this.extraHeaders = extraHeaders == null ? Map.of() : new LinkedHashMap<>(extraHeaders);
        // 部分网关（opencode zen）要求路由会话头；调用方有会话键就用它，否则每客户端一个 uuid。
        this.sessionKey = sessionKey == null || sessionKey.isEmpty()
                ? "mp-" + UUID.randomUUID().toString().replace("-", "") : sessionKey;
        this.http = httpClient == null
                ? HttpClient.newBuilder().connectTimeout(this.timeout).build() : httpClient;
    }

    // -- 请求头 ---------------------------------------------------------------

    /** 构造请求头；{@code x-opencode-session} 只在 opencode 网关且调用方未显式给出时注入。 */
    Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("content-type", "application/json");
        String key = apiKey.strip();
        if (!key.isEmpty()) {
            headers.put("authorization", "Bearer " + key);
        }
        headers.putAll(extraHeaders);
        boolean alreadySet = headers.keySet().stream()
                .anyMatch(name -> "x-opencode-session".equalsIgnoreCase(name));
        if (host().endsWith("opencode.ai") && !alreadySet) {
            headers.put("x-opencode-session", sessionKey);
        }
        return headers;
    }

    private String host() {
        try {
            String host = URI.create(baseUrl).getHost();
            return host == null ? "" : host;
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    /** 拼接补全端点：已以 {@code /chat/completions} 结尾则原样使用。 */
    static String chatEndpoint(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.strip();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base.endsWith(CHAT_COMPLETIONS) ? base : base + CHAT_COMPLETIONS;
    }

    // -- 非流式 ---------------------------------------------------------------

    @Override
    public LlmResponse chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        requireConfig();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        HttpResponse<String> response = send(body);
        if (response.statusCode() != 200) {
            throw LlmError.httpError(response.statusCode());
        }
        Map<String, Object> message = firstMessage(response.body());
        List<ToolCall> calls = new ArrayList<>();
        Object rawCalls = message.get("tool_calls");
        if (rawCalls instanceof List<?> list) {
            for (Object raw : list) {
                if (raw instanceof Map<?, ?> call) {
                    ToolCall parsed = parseToolCall(call);
                    if (parsed != null) {
                        calls.add(parsed);
                    }
                }
            }
        }
        Object content = message.get("content");
        return new LlmResponse(content == null ? "" : String.valueOf(content), calls);
    }

    // -- 流式 -----------------------------------------------------------------

    @Override
    public Iterator<StreamChunk> streamChat(List<Map<String, Object>> messages,
                                            List<Map<String, Object>> tools) {
        requireConfig();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", true);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        HttpRequest request = buildRequest(body);
        HttpResponse<Stream<String>> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofLines());
        } catch (HttpTimeoutException e) {
            throw new LlmError("The model provider timed out.", "upstream_error", true);
        } catch (IOException e) {
            throw new LlmError("Could not reach the model provider.", "upstream_error", false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmError("Could not reach the model provider.", "upstream_error", false);
        }
        if (response.statusCode() != 200) {
            response.body().close();
            throw LlmError.httpError(response.statusCode());
        }
        // 惰性迭代：每行到达才解析，文本片立即交给调用方，不被攒批。
        return new SseChunkIterator(response.body());
    }

    @Override
    public List<StreamChunk> streamChatEvents(List<Map<String, Object>> messages,
                                              List<Map<String, Object>> tools) {
        List<StreamChunk> chunks = new ArrayList<>();
        Iterator<StreamChunk> iterator = streamChat(messages, tools);
        while (iterator.hasNext()) {
            chunks.add(iterator.next());
        }
        return chunks;
    }

    // -- 内部 -----------------------------------------------------------------

    private void requireConfig() {
        if ("anthropic".equals(protocol)) {
            throw new LlmError(
                    "Anthropic chat is not supported yet; choose another protocol.",
                    "bad_request", false);
        }
        if (baseUrl.isEmpty() || model.isEmpty()) {
            throw new LlmError(
                    "No chat model is configured. Add one under Settings → Language models.",
                    "bad_request", false);
        }
    }

    private HttpRequest buildRequest(Map<String, Object> body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(chatEndpoint(baseUrl)))
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(body)));
        headers().forEach(builder::header);
        return builder.build();
    }

    private HttpResponse<String> send(Map<String, Object> body) {
        try {
            return http.send(buildRequest(body), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new LlmError("The model provider timed out.", "upstream_error", true);
        } catch (IOException e) {
            throw new LlmError("Could not reach the model provider.", "upstream_error", false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmError("Could not reach the model provider.", "upstream_error", false);
        }
    }

    /**
     * 把上游 SSE 行流包装成惰性 chunk 迭代器。
     *
     * <p>每取一片才读一行；流结束或抛错时关闭底层连接，避免连接泄漏。
     * 文本片即时产出，工具调用在 {@link SseParser#finish()} 时一次性产出。
     */
    private static final class SseChunkIterator implements Iterator<StreamChunk> {

        private final Stream<String> lines;
        private final Iterator<String> source;
        private final SseParser parser = new SseParser();
        private final Deque<StreamChunk> queue = new ArrayDeque<>();
        private boolean finished;

        SseChunkIterator(Stream<String> lines) {
            this.lines = lines;
            this.source = lines.iterator();
        }

        @Override
        public boolean hasNext() {
            while (queue.isEmpty() && !finished) {
                advance();
            }
            return !queue.isEmpty();
        }

        @Override
        public StreamChunk next() {
            if (!hasNext()) {
                throw new NoSuchElementException("stream already ended");
            }
            return queue.removeFirst();
        }

        private void advance() {
            try {
                while (source.hasNext()) {
                    queue.addAll(parser.feed(source.next()));
                    if (!queue.isEmpty()) {
                        return;
                    }
                }
                queue.addAll(parser.finish());
                finished = true;
            } catch (RuntimeException e) {
                finished = true;
                throw e;
            } finally {
                if (finished) {
                    lines.close();
                }
            }
        }
    }

    private String writeJson(Map<String, Object> body) {
        try {
            return mapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new LlmError("Could not encode the request.", "bad_request", false);
        }
    }

    /** 取 {@code payload.choices[0].message}；结构不符时抛结构化错误。 */
    private Map<String, Object> firstMessage(String body) {
        Object parsed;
        try {
            parsed = mapper.readValue(body, Object.class);
        } catch (Exception e) {
            throw new LlmError(
                    "The model provider returned an unexpected payload.", "upstream_error", false);
        }
        if (!(parsed instanceof Map<?, ?> payload)) {
            throw new LlmError(
                    "The model provider returned an unexpected payload.", "upstream_error", false);
        }
        Object rawChoices = payload.get("choices");
        if (!(rawChoices instanceof List<?> choices) || choices.isEmpty()) {
            throw new LlmError(
                    "The model provider returned an unexpected payload.", "upstream_error", false);
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choice)) {
            throw new LlmError(
                    "The model provider returned an unexpected payload.", "upstream_error", false);
        }
        Object rawMessage = choice.get("message");
        if (!(rawMessage instanceof Map<?, ?> message)) {
            throw new LlmError(
                    "The model provider returned an unexpected payload.", "upstream_error", false);
        }
        return castMap(message);
    }

    /** 解析一个工具调用；无名字时返回 {@code null}（参考实现直接跳过）。 */
    private ToolCall parseToolCall(Map<?, ?> raw) {
        Object rawFunction = raw.get("function");
        Map<?, ?> function = rawFunction instanceof Map<?, ?> map ? map : Map.of();
        String name = function.get("name") == null ? "" : String.valueOf(function.get("name"));
        if (name.isEmpty()) {
            return null;
        }
        Object rawArguments = function.get("arguments");
        Map<String, Object> arguments = Map.of();
        boolean parseError = false;
        if (rawArguments instanceof Map<?, ?> map) {
            arguments = castMap(map);
        } else {
            String text = rawArguments == null ? "" : String.valueOf(rawArguments);
            if (!text.isEmpty()) {
                Object parsed = tryParse(text);
                if (parsed instanceof Map<?, ?> map) {
                    arguments = castMap(map);
                } else {
                    parseError = true;
                }
            }
        }
        Object id = raw.get("id");
        return new ToolCall(name, arguments, id == null ? "" : String.valueOf(id), parseError);
    }

    private Object tryParse(String json) {
        try {
            return mapper.readValue(json, Object.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
