package com.masterypath.settings;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 提供方探测（参考实现 {@code tests/test_settings_probe.py} 的前 10 例）。
 *
 * <p>用 JDK {@link HttpServer} 起真实本地端点：请求路径、鉴权头、状态码映射都被真实执行，
 * 不 mock 传输层（参考实现用 {@code httpx.MockTransport}，意图相同）。
 */
@Tag("api")
class ProviderProbeTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastPath = new AtomicReference<>("");
    private final AtomicReference<Map<String, List<String>>> lastHeaders = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = "{}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            Map<String, List<String>> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((name, values) ->
                    headers.put(name.toLowerCase(), new ArrayList<>(values)));
            lastHeaders.set(headers);
            byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
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

    // -- 纯函数 ---------------------------------------------------------------

    @Test
    @DisplayName("validBaseUrl：接受 http/https，拒绝凭据、非法端口与非 URL")
    void validBaseUrlRejectsCredentialsAndJunk() {
        assertThat(ProviderProbe.validBaseUrl("https://api.example.com/v1")).isTrue();
        assertThat(ProviderProbe.validBaseUrl("http://127.0.0.1:11434/v1")).isTrue();
        assertThat(ProviderProbe.validBaseUrl("ftp://api.example.com")).isFalse();
        assertThat(ProviderProbe.validBaseUrl("https://user:pass@api.example.com")).isFalse();
        assertThat(ProviderProbe.validBaseUrl("not a url")).isFalse();
        assertThat(ProviderProbe.validBaseUrl("")).isFalse();
    }

    @Test
    @DisplayName("classifyModel：排除词优先于嵌入提示词")
    void classifyModelUsesKeywords() {
        assertThat(ProviderProbe.classifyModel("gpt-4o-mini")).isEqualTo("llm");
        assertThat(ProviderProbe.classifyModel("deepseek-chat")).isEqualTo("llm");
        assertThat(ProviderProbe.classifyModel("text-embedding-3-small")).isEqualTo("embedding");
        assertThat(ProviderProbe.classifyModel("bge-m3")).isEqualTo("embedding");
        assertThat(ProviderProbe.classifyModel("whisper-1")).isEmpty();
        assertThat(ProviderProbe.classifyModel("dall-e-3")).isEmpty();
    }

    @Test
    @DisplayName("statusForHttp：401/403 → auth_error，404/405/501 → unavailable，429 → rate_limited")
    void statusVocabulary() {
        assertThat(ProviderProbe.statusForHttp(401)).isEqualTo("auth_error");
        assertThat(ProviderProbe.statusForHttp(403)).isEqualTo("auth_error");
        assertThat(ProviderProbe.statusForHttp(404)).isEqualTo("unavailable");
        assertThat(ProviderProbe.statusForHttp(405)).isEqualTo("unavailable");
        assertThat(ProviderProbe.statusForHttp(501)).isEqualTo("unavailable");
        assertThat(ProviderProbe.statusForHttp(429)).isEqualTo("rate_limited");
        assertThat(ProviderProbe.statusForHttp(500)).isEqualTo("http_error");
    }

    @Test
    @DisplayName("splitBaseUrl：剥掉推理端点后缀")
    void splitBaseUrlStripsInferenceSuffixes() {
        assertThat(ProviderProbe.splitBaseUrl("https://api.example.com/v1/chat/completions/"))
                .isEqualTo("https://api.example.com/v1");
        assertThat(ProviderProbe.splitBaseUrl("https://api.example.com/v1/embeddings"))
                .isEqualTo("https://api.example.com/v1");
        assertThat(ProviderProbe.splitBaseUrl("https://api.example.com/v1/")).isEqualTo(
                "https://api.example.com/v1");
    }

    // -- 探测 -----------------------------------------------------------------

    @Test
    @DisplayName("探测成功：路径以 /models 结尾、Bearer 头、按名称分类、结果不含凭据")
    void probeConnectedAndClassifies() {
        responseBody = "{\"data\":[{\"id\":\"gpt-4o\"},{\"id\":\"text-embedding-3-small\"}]}";

        Map<String, Object> result = ProviderProbe.probeProvider(
                "openai", baseUrl, "sk-secret", "", null);

        assertThat(result.get("status")).isEqualTo("connected");
        assertThat(lastPath.get()).isEqualTo("/v1/models");
        assertThat(lastHeaders.get()).containsEntry("authorization", List.of("Bearer sk-secret"));
        Map<String, Object> byId = new LinkedHashMap<>();
        for (Object item : (List<?>) result.get("models")) {
            Map<?, ?> model = (Map<?, ?>) item;
            byId.put(String.valueOf(model.get("id")), model.get("services"));
        }
        assertThat(byId).containsEntry("gpt-4o", List.of("llm"))
                .containsEntry("text-embedding-3-small", List.of("embedding"));
        assertThat(String.valueOf(result)).doesNotContain("sk-secret");
    }

    @Test
    @DisplayName("service 过滤：embedding 只留嵌入模型")
    void probeFiltersByService() {
        responseBody = "{\"data\":[{\"id\":\"gpt-4o\"},{\"id\":\"bge-m3\"}]}";

        Map<String, Object> embedding = ProviderProbe.fetchModels(
                "openai", baseUrl, "k", "embedding", null);

        List<String> ids = new ArrayList<>();
        for (Object item : (List<?>) embedding.get("models")) {
            ids.add(String.valueOf(((Map<?, ?>) item).get("id")));
        }
        assertThat(ids).containsExactly("bge-m3");
    }

    @Test
    @DisplayName("service 过滤后为空：状态仍是 connected，附 warning=no_models")
    void probeWarnsWhenNothingLeft() {
        responseBody = "{\"data\":[{\"id\":\"gpt-4o\"}]}";

        Map<String, Object> result = ProviderProbe.probeProvider(
                "openai", baseUrl, "k", "embedding", null);

        assertThat(result.get("status")).isEqualTo("connected");
        assertThat(result.get("warning")).isEqualTo("no_models");
    }

    @Test
    @DisplayName("探测状态：401 → auth_error，404 → unavailable，端口关闭 → unreachable")
    void probeStatusVocabulary() {
        status = 401;
        responseBody = "{\"error\":\"bad key\"}";
        assertThat(ProviderProbe.probeProvider("openai", baseUrl, "k", "", null).get("status"))
                .isEqualTo("auth_error");

        status = 404;
        responseBody = "{}";
        Map<String, Object> missing = ProviderProbe.probeProvider("openai", baseUrl, "k", "", null);
        assertThat(missing.get("status")).isEqualTo("unavailable");
        assertThat(missing.get("http_status")).isEqualTo(404);

        // 端口关闭：连接被拒 → unreachable（对应参考实现的 httpx.HTTPError 分支）。
        Map<String, Object> unreachable = ProviderProbe.probeProvider(
                "openai", "http://127.0.0.1:1/v1", "k", "", null);
        assertThat(unreachable.get("status")).isEqualTo("unreachable");
    }

    @Test
    @DisplayName("非法 URL → invalid_url；anthropic 不发请求直接 unavailable")
    void probeInvalidUrlAndAnthropic() {
        assertThat(ProviderProbe.probeProvider("openai", "javascript:alert(1)", null, "", null)
                .get("status")).isEqualTo("invalid_url");
        Map<String, Object> anthropic = ProviderProbe.probeProvider(
                "anthropic", "https://api.anthropic.com", null, "", null);
        assertThat(anthropic.get("status")).isEqualTo("unavailable");
        assertThat(anthropic.get("warning")).isEqualTo("manual_entry");
    }

    @Test
    @DisplayName("ollama 发现路径是 /api/tags，且取 name 作为 id")
    void ollamaDiscoveryPath() {
        responseBody = "{\"models\":[{\"name\":\"llama3:latest\"}]}";

        Map<String, Object> result = ProviderProbe.probeProvider(
                "ollama", baseUrl, "", "", null);

        assertThat(lastPath.get()).isEqualTo("/api/tags");
        assertThat(((Map<?, ?>) ((List<?>) result.get("models")).get(0)).get("id"))
                .isEqualTo("llama3:latest");
    }

    // -- 维度 -----------------------------------------------------------------

    @Test
    @DisplayName("detectDimension：从 {data:[{embedding:[...]}]} 读出 1536")
    void detectDimension() {
        responseBody = "{\"data\":[{\"embedding\":" + zeros(1536) + "}]}";

        Map<String, Object> result = ProviderProbe.detectDimension(
                "openai", baseUrl, "k", "text-embedding-3-small", null);

        assertThat(lastPath.get()).isEqualTo("/v1/embeddings");
        assertThat(result).containsEntry("status", "connected").containsEntry("dimension", 1536);
    }

    @Test
    @DisplayName("detectDimension：无法解析出向量 → unavailable / 0")
    void detectDimensionUnavailable() {
        responseBody = "{\"unexpected\":true}";

        Map<String, Object> result = ProviderProbe.detectDimension(
                "openai", baseUrl, "k", "m", null);

        assertThat(result).containsEntry("status", "unavailable").containsEntry("dimension", 0);
    }

    // -- 目录 -----------------------------------------------------------------

    @Test
    @DisplayName("厂商目录含 deepseek/openai/ollama/custom，且返回副本")
    void providerCatalog() {
        List<Map<String, Object>> catalog = ProviderProbe.providerCatalog();
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> item : catalog) {
            ids.add(String.valueOf(item.get("id")));
        }
        assertThat(ids).contains("deepseek", "openai", "ollama", "custom");
        assertThat(catalog.get(0).get("monogram")).isEqualTo("DS");
        assertThat(catalog.get(0).get("requires_key")).isEqualTo(true);
        assertThat(catalog.get(4).get("requires_key")).isEqualTo(false);

        catalog.get(0).put("id", "mutated");
        assertThat(ProviderProbe.providerCatalog().get(0).get("id")).isEqualTo("deepseek");
    }

    private static String zeros(int count) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append("0.0");
        }
        return builder.append(']').toString();
    }
}
