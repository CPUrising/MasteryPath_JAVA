package com.masterypath.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TC-A6：聚合序列化往返相等（spec 03 §A）。 */
class LearningProgressJsonTest {

    private final ObjectMapper mapper = JsonSupport.objectMapper();

    /** TC-A6：序列化再反序列化，关键字段全部相等。 */
    @Test
    void roundTripKeepsAllFields() throws Exception {
        LearningProgress original = new LearningProgress("p");
        original.setName("Rust 并发");
        original.setGoal("三个月掌握 Rust 并发");

        LearningModule module = new LearningModule("m", "并发基础", 0);
        module.setObjective("能解释线程与进程的取舍");
        module.setDisplayTarget(0.8);

        KnowledgePoint kp = new KnowledgePoint("k1", "理解线程与进程", KnowledgeType.CONCEPT, "m");
        kp.setPrerequisiteIds(List.of("k0"));
        kp.setSourceIds(List.of("s1"));
        kp.setRequiredEvidence(List.of("quiz"));
        module.getKnowledgePoints().add(kp);
        original.getModules().add(module);

        original.getMasteryLevels().put("k1", 0.85);
        original.getQualitativeMastery().put("k1", true);
        original.getKnowledgeTypes().put("k1", KnowledgeType.CONCEPT);

        RepetitionState state = new RepetitionState();
        state.setNextReviewAt(1730000000.0);
        state.setIntervalIndex(2);
        state.setScheduledAfterFailure(true);
        original.getRepetitionStates().put("k1", state);

        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q1");
        pending.setKnowledgePointId("k1");
        pending.setQuestionType(PendingQuestion.QuestionType.CHOICE);
        pending.setOptions(List.of("A: x", "B: y"));
        original.setPendingQuestion(pending);

        original.setDesiredRetention(0.95);
        original.setVersion(7);

        String json = mapper.writeValueAsString(original);
        LearningProgress restored = mapper.readValue(json, LearningProgress.class);

        assertThat(restored.getPathId()).isEqualTo("p");
        assertThat(restored.getName()).isEqualTo("Rust 并发");
        assertThat(restored.getGoal()).isEqualTo("三个月掌握 Rust 并发");
        assertThat(restored.getDesiredRetention()).isEqualTo(0.95);
        assertThat(restored.getVersion()).isEqualTo(7);

        LearningModule restoredModule = restored.getModules().get(0);
        assertThat(restoredModule.getObjective()).isEqualTo("能解释线程与进程的取舍");
        assertThat(restoredModule.getDisplayTarget()).isEqualTo(0.8);

        KnowledgePoint restoredKp = restoredModule.getKnowledgePoints().get(0);
        assertThat(restoredKp.getPrerequisiteIds()).containsExactly("k0");
        assertThat(restoredKp.getSourceIds()).containsExactly("s1");
        assertThat(restoredKp.getRequiredEvidence()).containsExactly("quiz");
        assertThat(restoredKp.getType()).isEqualTo(KnowledgeType.CONCEPT);

        assertThat(restored.getMasteryLevels()).containsEntry("k1", 0.85);
        assertThat(restored.getQualitativeMastery()).containsEntry("k1", true);
        assertThat(restored.getRepetitionStates().get("k1").isScheduledAfterFailure()).isTrue();
        assertThat(restored.getPendingQuestion().choiceMap()).containsEntry("A", "x");

        // 二次往返必须稳定（防止序列化不稳定字段导致漂移）。
        assertThat(mapper.readTree(mapper.writeValueAsString(restored)))
                .isEqualTo(mapper.readTree(json));
    }

    /** 反序列化后仍能按 id 定位知识点（findKnowledgePoint 契约）。 */
    @Test
    void findKnowledgePointAfterRoundTrip() throws Exception {
        LearningProgress original = new LearningProgress("p");
        LearningModule module = new LearningModule("m", "M", 0);
        module.getKnowledgePoints().add(new KnowledgePoint("k1", "ab", KnowledgeType.MEMORY, "m"));
        original.getModules().add(module);

        LearningProgress restored = mapper.readValue(mapper.writeValueAsString(original),
                LearningProgress.class);
        LearningProgress.KnowledgePointLocation found = restored.findKnowledgePoint("k1");

        assertThat(found.found()).isTrue();
        assertThat(found.knowledgePoint().getName()).isEqualTo("ab");
        assertThat(found.module().getId()).isEqualTo("m");
        assertThat(restored.findKnowledgePoint("nope").found()).isFalse();
    }

    /** 空聚合也能往返（对应 TC-A6 的参考实现用例）。 */
    @Test
    void emptyProgressRoundTrip() throws Exception {
        LearningProgress original = new LearningProgress("p");
        original.getModules().add(new LearningModule("m", "M", 0));

        LearningProgress restored = mapper.readValue(mapper.writeValueAsString(original),
                LearningProgress.class);

        assertThat(restored.getPathId()).isEqualTo("p");
        assertThat(restored.getModules()).hasSize(1);
        assertThat(restored.getModules().get(0).getName()).isEqualTo("M");
    }
}
