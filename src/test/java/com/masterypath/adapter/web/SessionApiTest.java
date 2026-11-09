package com.masterypath.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

/**
 * 会话与模式端点（{@code test_f0_api.py#test_sessions_and_mode} 等）。
 *
 * <p>模式写入的三级校验顺序是本类的核心断言：422（模式值）→ 404（路径）→ 409（准入）。
 */
@Tag("api")
class SessionApiTest {

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
    void setModeCreatesSessionAndListsIt() throws Exception {
        api.buildOutline("p");

        Map<String, Object> body = json(api.mvc.perform(put("/learning/paths/p/sessions/s1/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"outline\",\"actor\":\"learner\"}"))
                .andReturn());

        assertThat(body).containsEntry("session_id", "s1")
                .containsEntry("mode", "outline")
                .containsEntry("actor", "learner");

        Map<String, Object> listed = json(
                api.mvc.perform(get("/learning/paths/p/sessions")).andReturn());
        assertThat(listed).containsEntry("path_id", "p");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sessions = (List<Map<String, Object>>) listed.get("sessions");
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0)).containsEntry("mode", "outline");
    }

    @Test
    void setModeRejectsUnknownValueWith422() throws Exception {
        api.buildOutline("p");
        assertThat(api.mvc.perform(put("/learning/paths/p/sessions/s1/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"banana\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void setModeRejectsUnknownPathWith404() throws Exception {
        assertThat(api.mvc.perform(put("/learning/paths/nope/sessions/s1/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"outline\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void setModeRejectsStudyWithoutOutlineWith409() throws Exception {
        api.service.createPath("p", "g", "n");
        assertThat(api.mvc.perform(put("/learning/paths/p/sessions/s1/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"study\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void modeValueIsCheckedBeforePathExistence() throws Exception {
        // 路径不存在 + 模式非法 → 422（模式先校验），不是 404。
        assertThat(api.mvc.perform(put("/learning/paths/nope/sessions/s1/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"banana\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void modeIsNormalisedToLowerCase() throws Exception {
        api.buildOutline("p");
        Map<String, Object> body = json(api.mvc.perform(put("/learning/paths/p/sessions/s1/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"  OUTLINE  \"}"))
                .andReturn());
        assertThat(body).containsEntry("mode", "outline");
    }

    @Test
    void actorDefaultsToLearner() throws Exception {
        api.buildOutline("p");
        Map<String, Object> body = json(api.mvc.perform(put("/learning/paths/p/sessions/s1/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"outline\"}"))
                .andReturn());
        assertThat(body).containsEntry("actor", "learner");
    }

    @Test
    void getSessionReturnsNestedSessionAndMessages() throws Exception {
        api.buildOutline("p");
        api.service.setSessionMode("p", "s1", "outline");

        Map<String, Object> detail = json(api.mvc.perform(get("/learning/sessions/s1")).andReturn());

        assertThat(detail).containsKeys("session", "messages");
        @SuppressWarnings("unchecked")
        Map<String, Object> session = (Map<String, Object>) detail.get("session");
        assertThat(session).containsEntry("session_id", "s1");
        assertThat((List<?>) detail.get("messages")).isEmpty();
    }

    @Test
    void getUnknownSessionIs404() throws Exception {
        assertThat(api.mvc.perform(get("/learning/sessions/nope"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void deleteSessionReturns204Then404() throws Exception {
        api.buildOutline("p");
        api.service.setSessionMode("p", "s1", "outline");

        assertThat(api.mvc.perform(delete("/learning/sessions/s1"))
                .andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(api.mvc.perform(delete("/learning/sessions/s1"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void deleteSessionDropsOwnedEmptyScratchPath() throws Exception {
        api.service.ensureScratchPath("s1");
        // 会话行必须先存在：deleteSession 对未知会话返回 false（参考实现同样如此）。
        api.service.setSessionMode("scratch-s1", "s1", "study");
        assertThat(api.store.load("scratch-s1")).isNotNull();

        assertThat(api.mvc.perform(delete("/learning/sessions/s1"))
                .andReturn().getResponse().getStatus()).isEqualTo(204);

        assertThat(api.store.load("scratch-s1")).isNull();
    }

    @Test
    void patchSessionWritesPreferencesDict() throws Exception {
        api.buildOutline("p");
        api.service.setSessionMode("p", "s1", "outline");

        Map<String, Object> body = json(api.mvc.perform(patch("/learning/paths/p/sessions/s1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferences\":{\"llm_selection\":{\"model\":\"m1\"}}}"))
                .andReturn());

        @SuppressWarnings("unchecked")
        Map<String, Object> preferences = (Map<String, Object>) body.get("preferences");
        assertThat(preferences).containsKey("llm_selection");
    }

    @Test
    void patchSessionAssemblesPreferencesFromKnownKeys() throws Exception {
        api.buildOutline("p");
        api.service.setSessionMode("p", "s1", "outline");

        Map<String, Object> body = json(api.mvc.perform(patch("/learning/paths/p/sessions/s1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"llm_selection\":{\"model\":\"m1\"},\"knowledge_bases\":[\"kb1\"]}"))
                .andReturn());

        @SuppressWarnings("unchecked")
        Map<String, Object> preferences = (Map<String, Object>) body.get("preferences");
        assertThat(preferences).containsKeys("llm_selection", "knowledge_bases");
    }

    @Test
    void patchSessionCreatesMissingSessionFirst() throws Exception {
        api.buildOutline("p");

        Map<String, Object> body = json(api.mvc.perform(patch("/learning/paths/p/sessions/s9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferences\":{\"a\":1},\"mode\":\"study\"}"))
                .andReturn());

        assertThat(body).containsEntry("session_id", "s9").containsEntry("mode", "study");
    }

    private static Map<String, Object> json(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private static List<Map<String, Object>> jsonList(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), List.class);
    }
}
