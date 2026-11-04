package com.masterypath.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 字段名契约：旧库 JSON 与前端契约都用 {@code snake_case}。
 *
 * <p>这些字段名一旦漂移，Java 版就读不懂参考实现写出的数据（数据等价性维度）。
 */
class JsonFieldNameTest {

    private final ObjectMapper mapper = JsonSupport.objectMapper();

    @Test
    void progressUsesSnakeCase() throws Exception {
        LearningProgress progress = new LearningProgress("p");
        progress.setOwnerSessionId("s1");
        progress.setCurrentModuleId("m");
        progress.setCurrentKpIndex(2);
        progress.getMasteryLevels().put("k1", 0.9);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(progress));

        assertThat(node.has("path_id")).isTrue();
        assertThat(node.has("owner_session_id")).isTrue();
        assertThat(node.has("current_module_id")).isTrue();
        assertThat(node.has("current_kp_index")).isTrue();
        assertThat(node.has("mastery_levels")).isTrue();
        assertThat(node.has("desired_retention")).isTrue();
        assertThat(node.has("pending_question")).isTrue();
        assertThat(node.has("pathId")).isFalse();
    }

    @Test
    void moduleAndKnowledgePointUseSnakeCase() throws Exception {
        LearningModule module = new LearningModule("m", "M", 1);
        module.setDisplayTarget(0.7);
        KnowledgePoint kp = new KnowledgePoint("k", "ab", KnowledgeType.MEMORY, "m");
        kp.setPrerequisiteIds(List.of("k0"));
        kp.setSourceIds(List.of("s1"));
        kp.setRequiredEvidence(List.of("quiz"));
        module.getKnowledgePoints().add(kp);

        JsonNode moduleNode = mapper.readTree(mapper.writeValueAsString(module));
        assertThat(moduleNode.has("display_target")).isTrue();
        assertThat(moduleNode.has("knowledge_points")).isTrue();

        JsonNode kpNode = moduleNode.get("knowledge_points").get(0);
        assertThat(kpNode.has("module_id")).isTrue();
        assertThat(kpNode.has("prerequisite_ids")).isTrue();
        assertThat(kpNode.has("source_ids")).isTrue();
        assertThat(kpNode.has("required_evidence")).isTrue();
    }

    @Test
    void booleanFieldsKeepPythonNames() throws Exception {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuestionId("q");
        attempt.setKnowledgePointId("k");
        attempt.setCorrect(true);
        attempt.setVoided(true);
        attempt.setIndependent(false);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(attempt));

        assertThat(node.get("is_correct").asBoolean()).isTrue();
        assertThat(node.get("voided").asBoolean()).isTrue();
        assertThat(node.get("independent").asBoolean()).isFalse();
        assertThat(node.has("correct")).isFalse();
    }

    @Test
    void repetitionStateUsesSnakeCase() throws Exception {
        RepetitionState state = new RepetitionState();
        state.setNextReviewAt(1.0);
        state.setEaseFactor(2.5);
        state.setScheduledAfterFailure(true);
        state.setDesiredRetention(0.9);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(state));

        assertThat(node.has("next_review_at")).isTrue();
        assertThat(node.has("ease_factor")).isTrue();
        assertThat(node.has("scheduled_after_failure")).isTrue();
        assertThat(node.has("desired_retention")).isTrue();
        assertThat(node.has("interval_index")).isTrue();
    }

    @Test
    void pendingQuestionHidesNothingButNamesAnswersInSnakeCase() throws Exception {
        PendingQuestion question = new PendingQuestion();
        question.setQuestionId("q");
        question.setKnowledgePointId("k");
        question.setExpectedAnswer("A");
        question.setQuestionType(PendingQuestion.QuestionType.CHOICE);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(question));

        assertThat(node.has("question_id")).isTrue();
        assertThat(node.has("knowledge_point_id")).isTrue();
        assertThat(node.has("question_type")).isTrue();
        assertThat(node.has("expected_answer")).isTrue();
        assertThat(node.has("accepted_answers")).isTrue();
    }
}
