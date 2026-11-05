package com.masterypath.settings;

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
 * 提供方连通性与模型发现（只读，参考实现 {@code settings/providers.py}）。
 *
 * <p>三个操作，全部<b>只读</b>：
 * <ol>
 *   <li>{@link #probeProvider} —— 校验凭据并列出模型。</li>
 *   <li>{@link #fetchModels} —— 只要模型列表（「自动检测」选择器）。</li>
 *   <li>{@link #detectDimension} —— 一次极小的嵌入请求，只为拿到向量宽度。</li>
 * </ol>
 *
 * <p>三条硬性约束：
 * <ol>
 *   <li><b>永不写配置</b>。</li>
 *   <li><b>永不回显凭据</b>：失败只报状态词，不拼接上游响应体（响应体可能含 key 或 URL）。</li>
 *   <li><b>永不抛未处理异常</b>：失败统一变成 {@code {status, models}} 结构。</li>
 * </ol>
 */
public final class ProviderProbe {

    /** 探测超时（参考实现 {@code DEFAULT_TIMEOUT = 20.0}）。 */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    /**
     * 推理端点后缀：在发现前剥掉，使「base_url 已指向 {@code /chat/completions}」仍能从 API 根发现。
     */
    private static final List<String> INFERENCE_SUFFIXES = List.of(
            "/embeddings", "/chat/completions", "/responses", "/messages",
            "/completions", "/api/embed", "/api/embeddings");

    /** 嵌入模型的名称提示词。 */
    private static final List<String> EMBEDDING_HINTS = List.of(
            "embed", "bge-", "gte-", "e5-", "voyage", "nomic", "text-similarity");

    /** 非文本端点：否则会被误算成聊天模型。 */
    private static final List<String> EXCLUDED_HINTS = List.of(
            "whisper", "tts", "dall-e", "image", "video", "rerank",
            "moderation", "audio", "speech", "transcribe");

    /** 添加提供方面板提供的厂商（顺序即展示顺序）。 */
    private static final List<Map<String, Object>> CATALOG = List.of(
            catalogEntry("deepseek", "DeepSeek", "https://api.deepseek.com", "openai",
                    List.of("llm", "task"), true, "DS"),
            catalogEntry("openai", "OpenAI", "https://api.openai.com/v1", "openai",
                    List.of("llm", "task", "embedding"), true, "OA"),
            catalogEntry("anthropic", "Anthropic", "https://api.anthropic.com", "anthropic",
                    List.of("llm", "task"), true, "AN"),
            catalogEntry("azure", "Azure OpenAI", "", "openai",
                    List.of("llm", "task", "embedding"), true, "AZ"),
            catalogEntry("ollama", "Ollama", "http://127.0.0.1:11434/v1", "ollama",
                    List.of("llm", "task", "embedding"), false, "OL"),
            catalogEntry("custom", "Custom", "", "custom",
                    List.of("llm", "task", "embedding"), true, "CU"));

    private static final ObjectMapper MAPPER = JsonSupport.objectMapper();

    private ProviderProbe() {
    }

    // -- 探测 -----------------------------------------------------------------

    /**
     * 检查访问权限并列出模型，<b>不做计费推理调用</b>。
     *
     * @param protocol {@code openai} / {@code anthropic} / {@code ollama} / {@code custom}
     * @param baseUrl  提供方根地址
     * @param apiKey   凭据；只进请求头
     * @param service  {@code llm} / {@code embedding} / {@code task}；空串表示不过滤
     * @param client   注入的 HTTP 客户端；{@code null} 时内部新建（测试用本地端点）
     * @return {@code {status, models, http_status?, warning?}}
     */
    public static Map<String, Object> probeProvider(String protocol, String baseUrl,
                                                    String apiKey, String service,
                                                    HttpClient client) {
        String kind = normalizeProtocol(protocol);
        String url = baseUrl == null ? "" : baseUrl.strip();
        if ("anthropic".equals(kind)) {
            // Anthropic 没有模型列表端点：不发请求，让用户手填。
            return payload("status", "unavailable", "models", new ArrayList<>(),
                    "warning", "manual_entry");
        }
        if (!validBaseUrl(url)) {
            return payload("status", "invalid_url", "models", new ArrayList<>());
        }
        Map<String, Object> response = get(discoveryRequest(kind, url),
                authHeaders(kind, apiKey), client);
        if (response.containsKey("error")) {
            return payload("status", response.get("error"), "models", new ArrayList<>());
        }
        int statusCode = (Integer) response.get("http_status");
        if (statusCode != 200) {
            return payload("status", statusForHttp(statusCode),
                    "models", new ArrayList<>(), "http_status", statusCode);
        }
        List<?> items = modelItems(response.get("payload"));
        if (items == null) {
            return payload("status", "unavailable", "models", new ArrayList<>());
        }
        List<Map<String, Object>> models = classifyItems(items);
        String wanted = "task".equals(service) ? "llm" : (service == null ? "" : service);
        if ("llm".equals(wanted) || "embedding".equals(wanted)) {
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> item : models) {
                if (listOf(item.get("services")).contains(wanted)) {
                    filtered.add(item);
                }
            }
            models = filtered;
        }
        Map<String, Object> result = payload("status", "connected", "models", models);
        if (models.isEmpty()) {
            result.put("warning", "no_models");
        }
        return result;
    }

    /** 探测的模型列表部分，供「自动检测」选择器使用（丢掉 {@code http_status} 与 {@code warning}）。 */
    public static Map<String, Object> fetchModels(String protocol, String baseUrl,
                                                  String apiKey, String service,
                                                  HttpClient client) {
        Map<String, Object> result = probeProvider(protocol, baseUrl, apiKey, service, client);
        return payload("status", result.get("status"),
                "models", result.getOrDefault("models", new ArrayList<>()));
    }

    /**
     * 发一次极小的嵌入请求，只为拿到向量宽度。
     *
     * <p>这是<b>唯一会花一点钱</b>的探测，因此只在用户显式点击时执行；
     * 不支持的端点返回 {@code {status: unavailable, dimension: 0}}（前端读作「请手填」）。
     */
    public static Map<String, Object> detectDimension(String protocol, String baseUrl,
                                                      String apiKey, String model,
                                                      HttpClient client) {
        String kind = normalizeProtocol(protocol);
        String url = baseUrl == null ? "" : baseUrl.strip();
        String modelId = model == null ? "" : model.strip();
        if (modelId.isEmpty() || !validBaseUrl(url)) {
            return payload("status", "invalid_url", "dimension", 0);
        }
        if ("anthropic".equals(kind)) {
            return payload("status", "unavailable", "dimension", 0);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelId);
        body.put("input", List.of("dimension probe"));
        Map<String, Object> response = post(embeddingRequest(kind, url),
                authHeaders(kind, apiKey), body, client, DEFAULT_TIMEOUT);
        if (response.containsKey("error")) {
            return payload("status", response.get("error"), "dimension", 0);
        }
        int statusCode = (Integer) response.get("http_status");
        if (statusCode != 200) {
            return payload("status", statusForHttp(statusCode),
                    "dimension", 0, "http_status", statusCode);
        }
        int dimension = dimensionFromPayload(response.get("payload"));
        if (dimension <= 0) {
            return payload("status", "unavailable", "dimension", 0);
        }
        return payload("status", "connected", "dimension", dimension);
    }

    // -- 纯函数 ---------------------------------------------------------------

    /** 只接受 {@code http}/{@code https}、带主机名、不带凭据的 URL。 */
    public static boolean validBaseUrl(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl == null ? "" : baseUrl.strip());
            // 取端口会校验非法/非数字端口（参考实现同样靠访问 .port 触发校验）。
            uri.getPort();
            String scheme = uri.getScheme();
            return ("http".equals(scheme) || "https".equals(scheme))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 剥掉已知的推理端点后缀（参考实现 {@code _split_base_url}）。 */
    static String splitBaseUrl(String baseUrl) {
        String text = baseUrl == null ? "" : baseUrl.strip();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        for (String suffix : INFERENCE_SUFFIXES) {
            if (text.endsWith(suffix)) {
                return text.substring(0, text.length() - suffix.length());
            }
        }
        return text;
    }

    /** 模型列表端点：Ollama 去 {@code /v1} 加 {@code /api/tags}，其余加 {@code /models}。 */
    static String discoveryRequest(String protocol, String baseUrl) {
        return rootUrl(baseUrl, "ollama".equals(normalizeProtocol(protocol))
                ? "/api/tags" : "/models", "ollama".equals(normalizeProtocol(protocol)));
    }

    /** 嵌入端点：Ollama 去 {@code /v1} 加 {@code /api/embed}，其余加 {@code /embeddings}。 */
    static String embeddingRequest(String protocol, String baseUrl) {
        boolean ollama = "ollama".equals(normalizeProtocol(protocol));
        return rootUrl(baseUrl, ollama ? "/api/embed" : "/embeddings", ollama);
    }

    /** 请求头三态：anthropic 用 {@code x-api-key}，ollama 不带，其余 Bearer；key 为空则全不带。 */
    static Map<String, String> authHeaders(String protocol, String apiKey) {
        Map<String, String> headers = new LinkedHashMap<>();
        String key = apiKey == null ? "" : apiKey.strip();
        if (key.isEmpty()) {
            return headers;
        }
        String kind = normalizeProtocol(protocol);
        if ("anthropic".equals(kind)) {
            headers.put("x-api-key", key);
            headers.put("anthropic-version", "2023-06-01");
        } else if (!"ollama".equals(kind)) {
            headers.put("Authorization", "Bearer " + key);
        }
        return headers;
    }

    /**
     * 按名称判定一个模型属于 {@code embedding} 还是 {@code llm}。
     *
     * <p>OpenAI 兼容列表里名称是唯一可靠信号，因此刻意用关键词；
     * <b>排除优先</b>——{@code whisper}/{@code dall-e} 之类先被丢弃，不会被当成聊天模型。
     *
     * @return {@code "embedding"} / {@code "llm"}；命中排除词时返回<b>空串</b>
     */
    public static String classifyModel(String modelId) {
        String name = modelId == null ? "" : modelId.toLowerCase(Locale.ROOT);
        for (String hint : EXCLUDED_HINTS) {
            if (name.contains(hint)) {
                return "";
            }
        }
        for (String hint : EMBEDDING_HINTS) {
            if (name.contains(hint)) {
                return "embedding";
            }
        }
        return "llm";
    }

    /** 厂商目录（返回副本，调用方改不到共享常量）。 */
    public static List<Map<String, Object>> providerCatalog() {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> item : CATALOG) {
            copy.add(new LinkedHashMap<>(item));
        }
        return copy;
    }

    // -- 内部 -----------------------------------------------------------------

    /** 从列表里取模型条目：{@code [..]} 或 {@code {data: [..]}} 或 {@code {models: [..]}}。 */
    static List<?> modelItems(Object payload) {
        if (payload instanceof List<?> list) {
            return list;
        }
        if (payload instanceof Map<?, ?> map) {
            for (String key : List.of("data", "models")) {
                Object value = map.get(key);
                if (value instanceof List<?> list) {
                    return list;
                }
            }
        }
        return null;
    }

    /** 单个条目的 id：字符串本身，或 {@code id}/{@code model}/{@code name} 中第一个非空字符串。 */
    static String modelId(Object item) {
        if (item instanceof CharSequence text) {
            return text.toString();
        }
        if (!(item instanceof Map<?, ?> map)) {
            return "";
        }
        for (String key : List.of("id", "model", "name")) {
            Object value = map.get(key);
            if (value instanceof CharSequence text && text.length() > 0) {
                return text.toString();
            }
        }
        return "";
    }

    /** 分类 + 去重（保持首次出现顺序）；{@code services} 恒为单元素列表。 */
    static List<Map<String, Object>> classifyItems(List<?> items) {
        Map<String, Map<String, Object>> seen = new LinkedHashMap<>();
        for (Object item : items) {
            String id = modelId(item);
            if (id.isEmpty() || seen.containsKey(id)) {
                continue;
            }
            String kind = classifyModel(id);
            if (kind.isEmpty()) {
                continue;
            }
            seen.put(id, payload("id", id, "services", List.of(kind)));
        }
        return new ArrayList<>(seen.values());
    }

    /** 状态码 → 状态词（词表与参考实现逐字一致）。 */
    static String statusForHttp(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return "auth_error";
        }
        if (statusCode == 404 || statusCode == 405 || statusCode == 501) {
            return "unavailable";
        }
        if (statusCode == 429) {
            return "rate_limited";
        }
        return "http_error";
    }

    /** 从 {@code {embeddings: [[..]]}} 或 {@code {data: [{embedding: [..]}]}} 取向量宽度。 */
    static int dimensionFromPayload(Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            return 0;
        }
        Object vectors = map.get("embeddings");
        if (vectors == null) {
            Object data = map.get("data");
            if (data instanceof List<?> list && !list.isEmpty()
                    && list.get(0) instanceof Map<?, ?> first) {
                Object embedding = first.get("embedding");
                return embedding instanceof List<?> values ? values.size() : 0;
            }
            return 0;
        }
        if (!(vectors instanceof List<?> list) || list.isEmpty()) {
            return 0;
        }
        return list.get(0) instanceof List<?> first ? first.size() : 0;
    }

    /**
     * GET 探测。
     *
     * @return {@code {http_status, payload}} 或 {@code {error: <状态词>}}
     */
    private static Map<String, Object> get(String url, Map<String, String> headers,
                                           HttpClient client) {
        HttpClient http = client == null
                ? HttpClient.newBuilder().connectTimeout(DEFAULT_TIMEOUT).build() : client;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .GET();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            return payload("error", "timeout");
        } catch (IOException e) {
            return payload("error", "unreachable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return payload("error", "unreachable");
        }
        return payload("http_status", response.statusCode(),
                "payload", parse(response.body()));
    }

    /** POST 探测；错误映射同 {@link #get}。 */
    private static Map<String, Object> post(String url, Map<String, String> headers,
                                            Map<String, Object> body, HttpClient client,
                                            Duration timeout) {
        HttpClient http = client == null
                ? HttpClient.newBuilder().connectTimeout(DEFAULT_TIMEOUT).build() : client;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        HttpResponse<String> response;
        try {
            response = http.send(builder.POST(HttpRequest.BodyPublishers.ofString(
                    MAPPER.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            return payload("error", "timeout");
        } catch (IOException e) {
            return payload("error", "unreachable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return payload("error", "unreachable");
        }
        return payload("http_status", response.statusCode(),
                "payload", parse(response.body()));
    }

    /** 坏 JSON 等价于 {@code null}（参考实现吞掉 {@code ValueError}）。 */
    private static Object parse(String body) {
        try {
            return MAPPER.readValue(body, Object.class);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 拼「根 + 端点」的 URL：保留 scheme 与 authority，丢掉 query 与 fragment
     * （参考实现 {@code urlunsplit(parsed._replace(path=..., query="", fragment=""))}）。
     */
    private static String rootUrl(String baseUrl, String suffix, boolean stripV1) {
        URI uri = URI.create(splitBaseUrl(baseUrl));
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (stripV1 && path.endsWith("/v1")) {
            path = path.substring(0, path.length() - "/v1".length());
        }
        String authority = uri.getRawAuthority() == null ? "" : uri.getRawAuthority();
        return uri.getScheme() + "://" + authority + path + suffix;
    }

    private static String normalizeProtocol(String protocol) {
        String kind = protocol == null ? "" : protocol.strip().toLowerCase(Locale.ROOT);
        return kind.isEmpty() ? "openai" : kind;
    }

    private static List<?> listOf(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static Map<String, Object> catalogEntry(String id, String name, String baseUrl,
                                                    String protocol, List<String> services,
                                                    boolean requiresKey, String monogram) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("name", name);
        entry.put("base_url", baseUrl);
        entry.put("protocol", protocol);
        entry.put("services", services);
        entry.put("requires_key", requiresKey);
        entry.put("monogram", monogram);
        return entry;
    }

    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }
}
