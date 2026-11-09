package com.masterypath.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.QuizFixtures;
import com.masterypath.domain.model.JsonSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

@Tag("api")
class QuizSetApiTest {

    private static final ObjectMapper MAPPER = JsonSupport.objectMapper();

    private ApiTestSupport api;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport();
        api.service.createPath("p", "learn caching", "Caching");
        api.store.saveQuizSet(QuizFixtures.set("s1", "p", 1),
                List.of(QuizFixtures.item("s1", 1, QuizFixtures.choice("q1", "A"))));
    }

    @AfterEach
    void tearDown() {
        api.close();
    }

    @Test
    void getHidesAnswersBeforeSubmit() throws Exception {
        MvcResult result = api.mvc.perform(get("/learning/paths/p/quiz-sets/s1")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(question(json(result))).doesNotContainKey("correct_answer");
    }

    @Test
    void submitReleasesAnswersAndIsIdempotent() throws Exception {
        Map<String, Object> first = json(api.mvc.perform(post("/learning/paths/p/quiz-sets/s1/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"question_id\":\"q1\",\"answer\":\"A\"}]}"))
                .andReturn());
        assertThat(first).containsEntry("status", "submitted");
        assertThat(question(first)).containsEntry("correct_answer", "A")
                .containsEntry("learner_answer", "A");

        Map<String, Object> replay = json(api.mvc.perform(post("/learning/paths/p/quiz-sets/s1/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"question_id\":\"q1\",\"answer\":\"B\"}]}"))
                .andReturn());
        assertThat(question(replay)).containsEntry("learner_answer", "A");
        assertThat(api.service.store().load("p").getLearningEvidence()).isEmpty();
    }

    @Test
    void emptyAnswersIs422() throws Exception {
        assertThat(api.mvc.perform(post("/learning/paths/p/quiz-sets/s1/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[]}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void unknownSetOrWrongPathIs404() throws Exception {
        assertThat(api.mvc.perform(get("/learning/paths/p/quiz-sets/nope"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(api.mvc.perform(get("/learning/paths/other/quiz-sets/s1"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> question(Map<String, Object> view) {
        return ((List<Map<String, Object>>) view.get("questions")).get(0);
    }

    private static Map<String, Object> json(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), Map.class);
    }
}
