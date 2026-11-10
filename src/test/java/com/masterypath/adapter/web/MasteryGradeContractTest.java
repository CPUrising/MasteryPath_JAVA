package com.masterypath.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 掌握判分的 REST 契约（{@code POST /grade} / {@code GET session} / {@code GET mastery}）。
 *
 * <p>断言的是真实控制器 + 真实工具层 + 真实 SQLite 的响应字段与落库，
 * 不是服务层裸返回值；浏览器与前端消费的就是这些键。
 */
@Tag("api")
class MasteryGradeContractTest {

    private static final ObjectMapper MAPPER = JsonSupport.objectMapper();

    private ApiTestSupport api;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport();
        api.buildOutline("p");
    }

    @AfterEach
    void tearDown() {
        api.close();
    }

    @Test
    @DisplayName("结构化答案判对，但首次正确只给 0.5")
    void structuredAnswerIsCorrectAndCapped() throws Exception {
        String questionId = registerShort("s1");

        Map<String, Object> graded = grade(questionId, "4", "s1");

        assertThat(graded)
                .containsEntry("ok", true)
                .containsEntry("status", "graded")
                .containsEntry("result", "correct")
                .containsEntry("is_correct", true)
                .containsEntry("gate_cleared", false)
                .containsEntry("question_id", questionId)
                .containsEntry("learner_answer", "4")
                .containsEntry("independent", true);
        assertThat(((Number) graded.get("mastery_after")).doubleValue()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("三次答对后掌握度到 1.0 并清门槛")
    void threeCorrectAnswersClearTheGate() throws Exception {
        Map<String, Object> last = null;
        for (int index = 0; index < 3; index++) {
            last = grade(registerShort("s1"), "4", "s1");
        }

        assertThat(last)
                .containsEntry("result", "correct")
                .containsEntry("gate_cleared", true);
        assertThat(((Number) last.get("mastery_after")).doubleValue()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("无法解析的选择题返回 422，且零状态变更")
    void unparseableChoiceIsUngraded() throws Exception {
        String questionId = registerChoice("s1");
        int evidenceBefore = api.service.store().load("p").getLearningEvidence().size();

        var response = api.mvc.perform(post("/learning/paths/p/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question_id\":\"" + questionId + "\",\"answer\":\"Z\","
                                + "\"session_id\":\"s1\"}"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getContentAsString()).contains("Not graded");
        assertThat(api.service.store().load("p").getLearningEvidence()).hasSize(evidenceBefore);
        assertThat(api.service.store().load("p").getPendingQuestion()).isNotNull();
    }

    @Test
    @DisplayName("判分反馈落进会话消息 data.grade")
    void feedbackPersistsInSessionMessages() throws Exception {
        String questionId = registerShort("s1");

        grade(questionId, "4", "s1");

        List<Map<String, Object>> messages = messages("s1");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).containsEntry("role", "user").containsEntry("content", "4");
        @SuppressWarnings("unchecked")
        Map<String, Object> gradeData = (Map<String, Object>) data(messages.get(1)).get("grade");
        assertThat(gradeData)
                .containsEntry("status", "graded")
                .containsEntry("result", "correct")
                .containsEntry("question_id", questionId);
        assertThat(((Number) gradeData.get("mastery_after")).doubleValue()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("掌握度端点反映门槛结果")
    @SuppressWarnings("unchecked")
    void masteryEndpointReflectsGate() throws Exception {
        for (int index = 0; index < 3; index++) {
            grade(registerShort("s1"), "4", "s1");
        }

        Map<String, Object> map = json(api.mvc.perform(get("/learning/paths/p/mastery"))
                .andReturn());
        Map<String, Object> entry = (Map<String, Object>) map.get("k1");
        assertThat(entry)
                .containsEntry("status", "mastered")
                .containsEntry("module_id", "m1")
                .containsEntry("type", "memory");
        assertThat(((Number) entry.get("mastery")).doubleValue()).isEqualTo(1.0);
    }

    // -- 辅助 -----------------------------------------------------------------

    private String registerShort(String sessionId) throws Exception {
        return String.valueOf(json(api.mvc.perform(post("/learning/paths/p/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge_point_id\":\"k1\",\"question\":\"2+2?\","
                                + "\"question_type\":\"short\",\"expected_answer\":\"4\","
                                + "\"session_id\":\"" + sessionId + "\"}"))
                .andReturn()).get("question_id"));
    }

    private String registerChoice(String sessionId) throws Exception {
        return String.valueOf(json(api.mvc.perform(post("/learning/paths/p/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge_point_id\":\"k1\",\"question\":\"pick one\","
                                + "\"question_type\":\"choice\",\"expected_answer\":\"A\","
                                + "\"options\":[{\"label\":\"A\",\"body\":\"one\"},"
                                + "{\"label\":\"B\",\"body\":\"two\"}],"
                                + "\"session_id\":\"" + sessionId + "\"}"))
                .andReturn()).get("question_id"));
    }

    private Map<String, Object> grade(String questionId, String answer, String sessionId)
            throws Exception {
        return json(api.mvc.perform(post("/learning/paths/p/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question_id\":\"" + questionId + "\",\"answer\":\""
                                + answer + "\",\"session_id\":\"" + sessionId + "\"}"))
                .andReturn());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> messages(String sessionId) {
        Map<String, Object> detail = api.service.getSessionDetail(sessionId);
        return (List<Map<String, Object>>) detail.get("messages");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> message) {
        return (Map<String, Object>) message.get("data");
    }

    private static Map<String, Object> json(org.springframework.test.web.servlet.MvcResult result)
            throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), Map.class);
    }
}
