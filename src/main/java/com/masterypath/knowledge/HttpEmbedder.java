package com.masterypath.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通过 OpenAI 兼容 {@code /embeddings} 或 Ollama {@code /api/embed} 取向量
 * （参考实现 {@code settings/providers.py#embed_texts}）。
 *
 * <p>三条硬性约束：
 * <ol>
 *   <li><b>API Key 不出本对象</b>：只进请求头，不进日志、不进异常消息。</li>
 *   <li><b>上游响应体不回显</b>：错误只报状态码或固定文案（响应体可能含凭据）。</li>
 *   <li><b>向量数量必须匹配</b>：少一条就报错，避免错位写入索引。</li>
 * </ol>
 */
public class HttpEmbedder implements VectorIndexer.Embedder {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final String protocol;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final ObjectMapper mapper = JsonSupport.objectMapper();

    /** 由 {@link com.masterypath.service.KnowledgeService#resolveEmbedding} 的配置构造。 */
    public HttpEmbedder(Map<String, Object> config) {
        this(config, null);
    }

    public HttpEmbedder(Map<String, Object> config, HttpClient client) {
        Map<String, Object> safe = config == null ? Map.of() : config;
        this.protocol = str(safe.get("protocol"), "openai").strip().toLowerCase(Locale.ROOT);
        this.baseUrl = str(safe.get("base_url"), "").strip();
        this.apiKey = str(safe.get("api_key"), "");
        this.model = str(safe.get("model"), "").strip();
        this.http = client == null
                ? HttpClient.newBuilder().connectTimeout(TIMEOUT).build() : client;
    }

    @Override
    public List<List<Double>> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (model.isEmpty() || !validBaseUrl(baseUrl)) {
            throw new VectorIndexer.IndexUnavailable(
                    "The embedding provider is not configured.");
        }
        if ("anthropic".equals(protocol)) {
            throw new VectorIndexer.IndexUnavailable(
                    "Anthropic does not expose an embeddings endpoint.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", texts);
        HttpRequest request;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(embeddingEndpoint()))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json");
            for (Map.Entry<String, String> header : headers().entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
            request = builder
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
        } catch (IOException e) {
            throw new VectorIndexer.IndexUnavailable("The embedding provider is not configured.");
        }
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new VectorIndexer.IndexUnavailable("The embedding request timed out.");
        } catch (IOException e) {
            throw new VectorIndexer.IndexUnavailable("Could not reach the embedding provider.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VectorIndexer.IndexUnavailable("Could not reach the embedding provider.");
        }
        if (response.statusCode() != 200) {
            throw new VectorIndexer.IndexUnavailable(
                    "The embedding provider returned HTTP " + response.statusCode() + ".");
        }
        List<List<Double>> vectors = parseVectors(response.body());
        if (vectors.size() != texts.size()) {
            throw new VectorIndexer.IndexUnavailable(
                    "The embedding provider returned the wrong vector count.");
        }
        return vectors;
    }

    // -- 内部 -----------------------------------------------------------------

    /** 端点拼接：Ollama 去掉 {@code /v1} 后加 {@code /api/embed}，其余加 {@code /embeddings}。 */
    String embeddingEndpoint() {
        String base = splitBaseUrl(baseUrl);
        String path = base;
        if ("ollama".equals(protocol)) {
            if (path.endsWith("/v1")) {
                path = path.substring(0, path.length() - "/v1".length());
            }
            return path + "/api/embed";
        }
        return path + "/embeddings";
    }

    /** 去掉已知的推理端点后缀，让「已指向 /chat/completions 的 base_url」仍能发现根路径。 */
    static String splitBaseUrl(String baseUrl) {
        String text = baseUrl == null ? "" : baseUrl.strip();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        for (String suffix : List.of("/embeddings", "/chat/completions", "/responses",
                "/messages", "/completions", "/api/embed", "/api/embeddings")) {
            if (text.endsWith(suffix)) {
                return text.substring(0, text.length() - suffix.length());
            }
        }
        return text;
    }

    /** 请求头：{@code anthropic} 用 {@code x-api-key}，{@code ollama} 不带，其余 Bearer。 */
    Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        if (apiKey.strip().isEmpty()) {
            return headers;
        }
        if ("anthropic".equals(protocol)) {
            headers.put("x-api-key", apiKey.strip());
            headers.put("anthropic-version", "2023-06-01");
        } else if (!"ollama".equals(protocol)) {
            headers.put("Authorization", "Bearer " + apiKey.strip());
        }
        return headers;
    }

    /** 解析 {@code {embeddings: [[...]]}} 或 {@code {data: [{embedding: [...]}]}}。 */
    List<List<Double>> parseVectors(String raw) {
        Object payload;
        try {
            payload = mapper.readValue(raw, Object.class);
        } catch (IOException e) {
            throw new VectorIndexer.IndexUnavailable(
                    "The embedding provider returned invalid JSON.");
        }
        if (!(payload instanceof Map<?, ?> map)) {
            throw new VectorIndexer.IndexUnavailable(
                    "The embedding endpoint returned an unexpected payload.");
        }
        Object vectors = map.get("embeddings");
        if (vectors == null) {
            Object data = map.get("data");
            if (data instanceof List<?> list) {
                List<Object> extracted = new ArrayList<>();
                for (Object item : list) {
                    extracted.add(item instanceof Map<?, ?> entry ? entry.get("embedding") : null);
                }
                vectors = extracted;
            }
        }
        if (!(vectors instanceof List<?> list)) {
            throw new VectorIndexer.IndexUnavailable(
                    "The embedding endpoint returned no vectors.");
        }
        List<List<Double>> parsed = new ArrayList<>();
        for (Object vector : list) {
            if (!(vector instanceof List<?> values) || values.isEmpty()) {
                throw new VectorIndexer.IndexUnavailable(
                        "The embedding endpoint returned an empty vector.");
            }
            List<Double> converted = new ArrayList<>();
            for (Object value : values) {
                converted.add(value instanceof Number number ? number.doubleValue() : 0.0);
            }
            parsed.add(converted);
        }
        return parsed;
    }

    /** 只接受 http/https 且带主机名、不带凭据的 URL。 */
    static boolean validBaseUrl(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl == null ? "" : baseUrl.strip());
            String scheme = uri.getScheme();
            return ("http".equals(scheme) || "https".equals(scheme))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String str(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }
}
