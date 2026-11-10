package com.masterypath.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
 * REST 学习端点（{@code test_f0_api.py#test_rest_quiz_and_grade} / {@code #test_rest_assess_and_skip}）。
 *
 * <p>核心断言：这些端点<b>复用工具层</b>，因此返回体里带 {@code question_card} 等
 * 工具 metadata，而不是服务层的裸返回值。
 */
@Tag("api")
class StudyApiTest {

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
    void quizThenGradeClosesTheLoop() throws Exception {
        api.buildMixedOutline("p");

        Map<String, Object> quiz = json(api.mvc.perform(post("/learning/paths/p/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge_point_id\":\"k1\",\"question\":\"2+2?\","
                                + "\"expected_answer\":\"4\",\"session_id\":\"s1\"}"))
                .andReturn());

        assertThat(quiz).containsEntry("ok", true).containsEntry("status", "registered");
        assertThat(quiz).containsKey("question_card");
        String questionId = String.valueOf(quiz.get("question_id"));

        Map<String, Object> graded = json(api.mvc.perform(post("/learning/paths/p/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question_id\":\"" + questionId + "\",\"answer\":\"4\"}"))
                .andReturn());

        assertThat(graded).containsEntry("status", "graded")
                .containsEntry("result", "correct")
                .containsEntry("gate_cleared", false);
        assertThat(graded.get("mastery_after")).isEqualTo(0.5);
    }

    @Test
    void quizWithoutExpectedAnswerIs422() throws Exception {
        api.buildMixedOutline("p");
        assertThat(api.mvc.perform(post("/learning/paths/p/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge_point_id\":\"k1\",\"question\":\"?\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void quizOnUnknownObjectiveIs422() throws Exception {
        api.buildMixedOutline("p");
        assertThat(api.mvc.perform(post("/learning/paths/p/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge_point_id\":\"nope\",\"question\":\"?\","
                                + "\"expected_answer\":\"x\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void quizReplaysOpenQuestionInsteadOfCreatingNewOne() throws Exception {
        api.buildMixedOutline("p");
        String first = String.valueOf(json(api.mvc.perform(post("/learning/paths/p/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge_point_id\":\"k1\",\"question\":\"q1?\","
                                + "\"expected_answer\":\"a1\"}"))
                .andReturn()).get("question_id"));

        Map<String, Object> second = json(api.mvc.perform(post("/learning/paths/p/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge_point_id\":\"k1\",\"question\":\"q2?\","
                                + "\"expected_answer\":\"a2\"}"))
                .andReturn());

        assertThat(second).containsEntry("status", "already_pending");
        @SuppressWarnings("unchecked")
        Map<String, Object> card = (Map<String, Object>) second.get("question_card");
        assertThat(card).containsEntry("question_id", first);
    }

    @Test
    void gradeUnknownQuestionIs422() throws Exception {
        api.buildMixedOutline("p");
        assertThat(api.mvc.perform(post("/learning/paths/p/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question_id\":\"nope\",\"answer\":\"x\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void assessConceptObjective() throws Exception {
        api.buildMixedOutline("p");

        Map<String, Object> assessed = json(api.mvc.perform(post("/learning/paths/p/assess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge_point_id\":\"k2\",\"passed\":true,"
                                + "\"evidence\":\"用自己的话解释了\"}"))
                .andReturn());

        assertThat(assessed).containsEntry("ok", true).containsEntry("passed", true);
    }

    @Test
    void assessMemoryObjectiveIs422() throws Exception {
        api.buildMixedOutline("p");
        assertThat(api.mvc.perform(post("/learning/paths/p/assess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge_point_id\":\"k1\",\"passed\":true}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void skipQuestionAbandonsIt() throws Exception {
        api.buildMixedOutline("p");
        String questionId = String.valueOf(json(api.mvc.perform(post("/learning/paths/p/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge_point_id\":\"k1\",\"question\":\"?\","
                                + "\"expected_answer\":\"x\"}"))
                .andReturn()).get("question_id"));

        Map<String, Object> skipped = json(api.mvc.perform(post("/learning/paths/p/skip-question")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question_id\":\"" + questionId + "\"}"))
                .andReturn());

        assertThat(skipped).containsEntry("status", "abandoned");
    }

    @Test
    void quizIsRefusedInOutlineMode() throws Exception {
        api.buildMixedOutline("p");
        api.service.setSessionMode("p", "s1", "outline");

        assertThat(api.mvc.perform(post("/learning/paths/p/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge_point_id\":\"k1\",\"question\":\"?\","
                                + "\"expected_answer\":\"x\",\"session_id\":\"s1\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void quizIsAllowedWhenModeIsUnrecorded() throws Exception {
        api.buildMixedOutline("p");

        Map<String, Object> quiz = json(api.mvc.perform(post("/learning/paths/p/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge_point_id\":\"k1\",\"question\":\"?\","
                                + "\"expected_answer\":\"x\",\"session_id\":\"unknown\"}"))
                .andReturn());

        assertThat(quiz).containsEntry("status", "registered");
    }

    @Test
    void gradeFeedbackPersistsInSessionMessages() throws Exception {
        api.buildMixedOutline("p");
        String questionId = registerNumericQuestion("s1");

        Map<String, Object> graded = json(api.mvc.perform(post("/learning/paths/p/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question_id\":\"" + questionId + "\",\"answer\":\"4\","
                                + "\"session_id\":\"s1\",\"turn_id\":\"t-1\"}"))
                .andReturn());

        assertThat(graded).containsEntry("status", "graded").containsEntry("result", "correct");
        List<Map<String, Object>> messages = messages("s1");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).containsEntry("role", "user").containsEntry("content", "4");
        assertThat(messages.get(1)).containsEntry("role", "assistant");
        @SuppressWarnings("unchecked")
        Map<String, Object> grade = (Map<String, Object>) data(messages.get(1)).get("grade");
        assertThat(grade).containsEntry("status", "graded")
                .containsEntry("result", "correct")
                .containsEntry("question_id", questionId);
    }

    @Test
    void unparseableChoiceIsIncorrectAndPersists() throws Exception {
        api.buildMixedOutline("p");
        String questionId = registerChoiceQuestion("s1");
        var before = api.service.store().load("p");
        int evidenceBefore = before.getLearningEvidence().size();

        Map<String, Object> graded = json(api.mvc.perform(post("/learning/paths/p/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question_id\":\"" + questionId + "\",\"answer\":\"Z\","
                                + "\"session_id\":\"s1\"}"))
                .andReturn());

        assertThat(graded).containsEntry("status", "graded")
                .containsEntry("result", "incorrect");
        var after = api.service.store().load("p");
        assertThat(after.getLearningEvidence()).hasSize(evidenceBefore + 1);
        List<Map<String, Object>> messages = messages("s1");
        assertThat(messages).hasSize(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> grade = (Map<String, Object>) data(messages.get(1)).get("grade");
        assertThat(grade).containsEntry("status", "graded")
                .containsEntry("result", "incorrect");
    }

    @Test
    void repeatedGradeDoesNotDuplicateMessages() throws Exception {
        api.buildMixedOutline("p");
        String questionId = registerNumericQuestion("s1");

        json(api.mvc.perform(post("/learning/paths/p/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question_id\":\"" + questionId + "\",\"answer\":\"4\","
                                + "\"session_id\":\"s1\"}"))
                .andReturn());
        Map<String, Object> replay = json(api.mvc.perform(post("/learning/paths/p/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question_id\":\"" + questionId + "\",\"answer\":\"4\","
                                + "\"session_id\":\"s1\"}"))
                .andReturn());

        assertThat(replay).containsEntry("status", "already_graded");
        assertThat(messages("s1")).hasSize(2);
    }

    @Test
    void naturalLanguageShortNeedsAcceptedAnswers() throws Exception {
        api.buildMixedOutline("p");
        assertThat(api.mvc.perform(post("/learning/paths/p/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge_point_id\":\"k1\",\"question\":\"what process?\","
                                + "\"question_type\":\"short\","
                                + "\"expected_answer\":\"the process of photosynthesis\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void numericShortIsAllowedWithoutAcceptedAnswers() throws Exception {
        api.buildMixedOutline("p");
        Map<String, Object> quiz = json(api.mvc.perform(post("/learning/paths/p/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge_point_id\":\"k1\",\"question\":\"2+2?\","
                                + "\"question_type\":\"short\",\"expected_answer\":\"4\"}"))
                .andReturn());
        assertThat(quiz).containsEntry("status", "registered");
    }

    @Test
    void shortWithAcceptedAnswersIsAllowed() throws Exception {
        api.buildMixedOutline("p");
        Map<String, Object> quiz = json(api.mvc.perform(post("/learning/paths/p/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge_point_id\":\"k1\",\"question\":\"what process?\","
                                + "\"question_type\":\"short\","
                                + "\"expected_answer\":\"the process of photosynthesis\","
                                + "\"accepted_answers\":[\"photosynthesis\"]}"))
                .andReturn());
        assertThat(quiz).containsEntry("status", "registered");
    }

    private String registerNumericQuestion(String sessionId) throws Exception {
        return String.valueOf(json(api.mvc.perform(post("/learning/paths/p/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge_point_id\":\"k1\",\"question\":\"2+2?\","
                                + "\"expected_answer\":\"4\",\"session_id\":\"" + sessionId + "\"}"))
                .andReturn()).get("question_id"));
    }

    private String registerChoiceQuestion(String sessionId) throws Exception {
        return String.valueOf(json(api.mvc.perform(post("/learning/paths/p/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge_point_id\":\"k1\",\"question\":\"pick one\","
                                + "\"question_type\":\"choice\",\"expected_answer\":\"A\","
                                + "\"options\":[{\"label\":\"A\",\"body\":\"one\"},"
                                + "{\"label\":\"B\",\"body\":\"two\"}],"
                                + "\"session_id\":\"" + sessionId + "\"}"))
                .andReturn()).get("question_id"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> messages(String sessionId) {
        return (List<Map<String, Object>>) api.service.getSessionDetail(sessionId).get("messages");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> message) {
        return (Map<String, Object>) message.get("data");
    }

    private static Map<String, Object> json(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), Map.class);
    }
}
