package com.masterypath.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 探测端点与「建议下一问」（参考实现 {@code test_settings_probe.py} 的后 3 例
 * + {@code test_task_model.py#test_api_suggest_question}）。
 *
 * <p>两个关键约束：探测<b>不写配置</b>、<b>不回显凭据</b>。
 */
@Tag("api")
class SettingsProbeApiTest {

    private static final ObjectMapper MAPPER = JsonSupport.objectMapper();

    private ApiTestSupport api;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport();
    }

    @AfterEach
    void tearDown() {
        api.close();
    }

    @Test
    @DisplayName("厂商目录：含 deepseek/openai/ollama/custom")
    void catalogEndpoint() throws Exception {
        List<Map<String, Object>> catalog = jsonList(
                api.mvc.perform(get("/settings/providers/catalog")).andReturn());
        assertThat(catalog).extracting(item -> item.get("id"))
                .contains("deepseek", "openai", "ollama", "custom");
    }

    @Test
    @DisplayName("protocol 默认 openai；llm 分组原样读回")
    void protocolDefaultAndBackCompat() throws Exception {
        List<Map<String, Object>> saved = jsonList(api.mvc.perform(put("/settings/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"id\":\"deepseek\",\"name\":\"DeepSeek\","
                                + "\"base_url\":\"https://api.deepseek.com\",\"api_key\":\"sk\"}]"))
                .andReturn());
        assertThat(saved.get(0)).containsEntry("protocol", "openai");
        assertThat(jsonList(api.mvc.perform(get("/settings/providers")).andReturn()).get(0))
                .containsEntry("protocol", "openai");

        String section = "{\"models\":[{\"id\":\"m1\",\"provider_id\":\"deepseek\","
                + "\"model\":\"deepseek-chat\",\"name\":\"Chat\",\"services\":[\"llm\"]}],"
                + "\"active_id\":\"m1\"}";
        Map<String, Object> written = json(api.mvc.perform(put("/settings/llm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(section))
                .andReturn());
        assertThat(written).containsEntry("active_id", "m1");
        assertThat(json(api.mvc.perform(get("/settings/llm")).andReturn())).isEqualTo(written);
    }

    @Test
    @DisplayName("探测端点：提交空 api_key 时用已保存的凭据，且不回显")
    void probeEndpointUsesSavedCredentials() throws Exception {
        api.mvc.perform(put("/settings/providers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"id\":\"p1\",\"name\":\"Local\","
                        + "\"base_url\":\"https://api.example.com/v1\","
                        + "\"api_key\":\"sk-saved\",\"protocol\":\"ollama\"}]"));

        Map<String, Object> result = json(api.mvc.perform(post("/settings/providers/probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider_id\":\"p1\",\"service\":\"llm\"}"))
                .andReturn());

        assertThat(result.get("status")).isEqualTo("connected");
        assertThat(api.probeSeen).containsEntry("protocol", "ollama")
                .containsEntry("base_url", "https://api.example.com/v1")
                .containsEntry("api_key", "sk-saved")
                .containsEntry("service", "llm");
        assertThat(String.valueOf(result)).doesNotContain("sk-saved");
    }

    @Test
    @DisplayName("探测端点：提交值优先于已保存值")
    void probeEndpointPrefersSubmittedFields() throws Exception {
        api.mvc.perform(put("/settings/providers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"id\":\"p1\",\"base_url\":\"https://saved.example.com\","
                        + "\"api_key\":\"sk-saved\",\"protocol\":\"ollama\"}]"));

        api.mvc.perform(post("/settings/providers/probe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider_id\":\"p1\",\"protocol\":\"openai\","
                        + "\"base_url\":\"https://typed.example.com\",\"api_key\":\"sk-typed\"}"));

        assertThat(api.probeSeen).containsEntry("protocol", "openai")
                .containsEntry("base_url", "https://typed.example.com")
                .containsEntry("api_key", "sk-typed");
    }

    @Test
    @DisplayName("探测端点：不写配置（providers 保持不变）")
    void probeEndpointDoesNotWriteSettings() throws Exception {
        api.mvc.perform(put("/settings/providers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"id\":\"p1\",\"base_url\":\"https://api.example.com/v1\"}]"));
        List<Map<String, Object>> before = jsonList(
                api.mvc.perform(get("/settings/providers")).andReturn());

        api.mvc.perform(post("/settings/providers/probe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider_id\":\"p1\",\"api_key\":\"sk-new\"}"));

        assertThat(jsonList(api.mvc.perform(get("/settings/providers")).andReturn()))
                .isEqualTo(before);
    }

    @Test
    @DisplayName("fetch-models 与 detect-dimension 端点可调用，维度请求带 model")
    void fetchModelsAndDetectDimensionEndpoints() throws Exception {
        Map<String, Object> fetched = json(api.mvc.perform(post("/settings/providers/fetch-models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"protocol\":\"openai\",\"base_url\":\"https://x.example.com\"}"))
                .andReturn());
        assertThat(fetched).containsEntry("status", "connected");

        api.dimensionResult = Map.of("status", "connected", "dimension", 1536);
        Map<String, Object> dimension = json(api.mvc.perform(post("/settings/models/detect-dimension")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"protocol\":\"openai\",\"base_url\":\"https://x.example.com\","
                                + "\"model\":\"m\"}"))
                .andReturn());
        assertThat(dimension).containsEntry("status", "connected")
                .containsEntry("dimension", 1536);
        assertThat(api.dimensionSeen).containsEntry("model", "m");
    }

    @Test
    @DisplayName("建议下一问：已建路径返回非空草稿")
    void suggestQuestionReturnsDraft() throws Exception {
        api.buildOutline("p");

        Map<String, Object> body = json(api.mvc.perform(get("/learning/paths/p/suggest-question"))
                .andReturn());

        assertThat(String.valueOf(body.get("suggestion"))).isNotEmpty();
    }

    @Test
    @DisplayName("建议下一问：未知路径 404")
    void suggestQuestionUnknownPath() throws Exception {
        MvcResult result = api.mvc.perform(get("/learning/paths/nope/suggest-question")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("unknown path");
    }

    private static Map<String, Object> json(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private static List<Map<String, Object>> jsonList(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), List.class);
    }
}
