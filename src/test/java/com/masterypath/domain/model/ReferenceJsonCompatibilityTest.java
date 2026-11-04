package com.masterypath.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/**
 * 数据等价性：Java 版必须能读懂参考实现（Python）真实写出的 JSON。
 *
 * <p>fixture 由 {@code ../MasteryPath} 的 {@code LearningProgress.model_dump_json()} 原样产出，
 * 未经手工改写。这条用例是「数据等价」维度的最小闭环：
 * 它不验证 Java 写出的东西自己能不能读，而是验证<b>跨语言</b>可读。
 */
class ReferenceJsonCompatibilityTest {

    private final ObjectMapper mapper = JsonSupport.objectMapper();

    @Test
    void readsReferenceImplementationJson() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/reference-progress.json")) {
            assertThat(in).as("缺少 fixture: fixtures/reference-progress.json").isNotNull();

            LearningProgress progress = mapper.readValue(in, LearningProgress.class);

            assertThat(progress.getPathId()).isEqualTo("p");
            assertThat(progress.getGoal()).isEqualTo("G");
            assertThat(progress.getDesiredRetention()).isEqualTo(0.95);
            assertThat(progress.getVersion()).isEqualTo(7);

            LearningModule module = progress.getModules().get(0);
            assertThat(module.getObjective()).isEqualTo("O");
            assertThat(module.getDisplayTarget()).isEqualTo(0.8);

            KnowledgePoint kp = module.getKnowledgePoints().get(0);
            assertThat(kp.getName()).isEqualTo("ab");
            assertThat(kp.getType()).isEqualTo(KnowledgeType.CONCEPT);
            assertThat(kp.getPrerequisiteIds()).containsExactly("k0");
            assertThat(kp.getRequiredEvidence()).containsExactly("quiz");

            assertThat(progress.getMasteryLevels()).containsEntry("k1", 0.85);
            assertThat(progress.getQualitativeMastery()).containsEntry("k1", true);

            RepetitionState state = progress.getRepetitionStates().get("k1");
            assertThat(state.getIntervalIndex()).isEqualTo(2);
            assertThat(state.getNextReviewAt()).isEqualTo(1730000000.0);
            assertThat(state.isScheduledAfterFailure()).isTrue();
            assertThat(state.getProvenance()).isEqualTo("system");

            PendingQuestion pending = progress.getPendingQuestion();
            assertThat(pending.getQuestionId()).isEqualTo("q1");
            assertThat(pending.getQuestionType()).isEqualTo(PendingQuestion.QuestionType.CHOICE);
            assertThat(pending.getExpectedAnswer()).isEqualTo("A");
            assertThat(pending.choiceMap()).containsEntry("A", "x").containsEntry("B", "y");
        }
    }

    /** 读入后再写出，关键字段仍与参考实现一致（不含时间戳）。 */
    @Test
    void writesBackCompatibleShape() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/reference-progress.json")) {
            LearningProgress progress = mapper.readValue(in, LearningProgress.class);
            var node = mapper.readTree(mapper.writeValueAsString(progress));

            assertThat(node.get("path_id").asText()).isEqualTo("p");
            assertThat(node.get("version").asInt()).isEqualTo(7);
            assertThat(node.get("modules").get(0).get("display_target").asDouble()).isEqualTo(0.8);
            assertThat(node.get("modules").get(0).get("knowledge_points").get(0)
                    .get("type").asText()).isEqualTo("concept");
            assertThat(node.get("repetition_states").get("k1")
                    .get("scheduled_after_failure").asBoolean()).isTrue();
            assertThat(node.get("pending_question").get("question_type").asText()).isEqualTo("choice");
        }
    }
}
