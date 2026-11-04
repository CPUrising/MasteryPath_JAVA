package com.masterypath.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

/**
 * 设置分组读写（{@code test_f0_api.py#test_settings_roundtrip} /
 * {@code #test_model_section_list_roundtrip}）。
 *
 * <p>本 change 只做内存实现，落盘与提供方探测属 {@code 0021}。
 */
class SettingsApiTest {

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
    void defaultsMatchReference() throws Exception {
        Map<String, Object> all = json(api.mvc.perform(get("/settings")).andReturn());
        assertThat(all).containsKeys("providers", "llm", "task-models", "embedding", "general", "indexing");
        assertThat(all.get("llm")).isEqualTo(Map.of("models", List.of(), "active_id", ""));
        assertThat(all.get("indexing")).isEqualTo(Map.of("chunk_size", 512, "chunk_overlap", 64));
    }

    @Test
    void providersRoundtrip() throws Exception {
        String providers = "[{\"id\":\"deepseek\",\"name\":\"DeepSeek\","
                + "\"base_url\":\"https://api.deepseek.com\",\"api_key\":\"sk-x\","
                + "\"protocol\":\"openai\"}]";

        List<Map<String, Object>> written = jsonList(api.mvc.perform(put("/settings/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(providers))
                .andReturn());
        assertThat(written).hasSize(1);
        assertThat(written.get(0)).containsEntry("id", "deepseek");

        List<Map<String, Object>> read = jsonList(
                api.mvc.perform(get("/settings/providers")).andReturn());
        assertThat(read).isEqualTo(written);
    }

    @Test
    void unknownSectionIs404() throws Exception {
        assertThat(api.mvc.perform(get("/settings/nope"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(api.mvc.perform(put("/settings/nope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void modelSectionRoundtrip() throws Exception {
        String section = "{\"models\":[{\"id\":\"m1\",\"provider_id\":\"deepseek\","
                + "\"model\":\"deepseek-chat\",\"name\":\"Chat\"}],\"active_id\":\"m1\"}";

        Map<String, Object> written = json(api.mvc.perform(put("/settings/llm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(section))
                .andReturn());
        assertThat(written).containsEntry("active_id", "m1");

        Map<String, Object> read = json(api.mvc.perform(get("/settings/llm")).andReturn());
        assertThat(read).isEqualTo(written);
    }

    @Test
    void legacySingleModelFoldsIntoList() throws Exception {
        Map<String, Object> legacy = json(api.mvc.perform(put("/settings/embedding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"ollama\",\"model\":\"bge-m3\"}"))
                .andReturn());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> models = (List<Map<String, Object>>) legacy.get("models");
        assertThat(models).hasSize(1);
        assertThat(models.get(0)).containsEntry("model", "bge-m3")
                .containsEntry("provider_id", "ollama")
                .containsEntry("id", "m1");
    }

    @Test
    void unknownProtocolFallsBackToOpenai() throws Exception {
        List<Map<String, Object>> written = jsonList(api.mvc.perform(put("/settings/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"id\":\"x\",\"protocol\":\"banana\"}]"))
                .andReturn());
        assertThat(written.get(0)).containsEntry("protocol", "openai");
    }

    @Test
    void nonDictDetectionIsDropped() throws Exception {
        List<Map<String, Object>> written = jsonList(api.mvc.perform(put("/settings/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"id\":\"x\",\"detection\":\"junk\"}]"))
                .andReturn());
        assertThat(written.get(0)).doesNotContainKey("detection");
    }

    @Test
    void dictDetectionIsPreserved() throws Exception {
        List<Map<String, Object>> written = jsonList(api.mvc.perform(put("/settings/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"id\":\"x\",\"detection\":{\"ok\":true}}]"))
                .andReturn());
        assertThat(written.get(0)).containsKey("detection");
    }

    @Test
    void generalSectionRoundtrip() throws Exception {
        Map<String, Object> written = json(api.mvc.perform(put("/settings/general")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"interface_language\":\"en\",\"response_language\":\"en\"}"))
                .andReturn());
        assertThat(written).containsEntry("interface_language", "en");
    }

    private static Map<String, Object> json(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private static List<Map<String, Object>> jsonList(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), List.class);
    }
}
