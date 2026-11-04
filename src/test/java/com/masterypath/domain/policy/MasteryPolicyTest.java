package com.masterypath.domain.policy;

import static com.masterypath.domain.policy.PolicyFixtures.kp;
import static com.masterypath.domain.policy.PolicyFixtures.module;
import static com.masterypath.domain.policy.PolicyFixtures.progress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearnerMasteryOverride;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.domain.model.QuizAttempt;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** TC-C1…C17、TC-F1、TC-F6/F7/F8：掌握门槛与推进（spec 03 §C）。 */
class MasteryPolicyTest {

    private static final KnowledgeType M = KnowledgeType.MEMORY;
    private static final KnowledgeType P = KnowledgeType.PROCEDURE;
    private static final KnowledgeType C = KnowledgeType.CONCEPT;
    private static final KnowledgeType D = KnowledgeType.DESIGN;

    /** 构造「一个模块一个知识点」的最小聚合。 */
    private static LearningProgress one(KnowledgeType type) {
        return progress(List.of(module("m1", "M1", 0,
                List.of(kp("k1", "objective", type)))));
    }

    private static KnowledgePoint firstKp(LearningProgress progress) {
        return progress.getModules().get(0).getKnowledgePoints().get(0);
    }

    // -- TC-C1 / C2 / C3：量化门槛 -------------------------------------------

    /** TC-C1：memory 0.85 < 0.9 → 未掌握。 */
    @Test
    void quantitativeBelowGate() {
        LearningProgress progress = one(M);
        progress.getMasteryLevels().put("k1", 0.85);
        KnowledgePoint point = firstKp(progress);

        assertThat(MasteryPolicy.isAssessedMastered(progress, point)).isFalse();
        assertThat(MasteryPolicy.isMastered(progress, point)).isFalse();
    }

    /** TC-C2：memory 0.90 >= 0.9 → 掌握，来源 system。 */
    @Test
    void quantitativeAtGate() {
        LearningProgress progress = one(M);
        progress.getMasteryLevels().put("k1", 0.90);
        KnowledgePoint point = firstKp(progress);

        assertThat(MasteryPolicy.isAssessedMastered(progress, point)).isTrue();
        assertThat(MasteryPolicy.masterySource(progress, point)).isEqualTo("system");
    }

    /** TC-C3：procedure 满分 → 掌握。 */
    @Test
    void procedureFullMastery() {
        LearningProgress progress = one(P);
        progress.getMasteryLevels().put("k1", 1.0);

        assertThat(MasteryPolicy.isMastered(progress, firstKp(progress))).isTrue();
    }

    // -- TC-C4 / C5：定性门槛 -------------------------------------------------

    /** TC-C4：concept 分数满分但无定性通过 → 未掌握（INV-05）。 */
    @Test
    void conceptIgnoresScore() {
        LearningProgress progress = one(C);
        progress.getMasteryLevels().put("k1", 1.0);

        assertThat(MasteryPolicy.isMastered(progress, firstKp(progress))).isFalse();
    }

    /** TC-C5：concept 定性通过 → 掌握，即使分数为 0。 */
    @Test
    void conceptQualitativePass() {
        LearningProgress progress = one(C);
        progress.getQualitativeMastery().put("k1", true);
        progress.getMasteryLevels().put("k1", 0.0);

        assertThat(MasteryPolicy.isMastered(progress, firstKp(progress))).isTrue();
    }

    // -- TC-C6 / C7 / C8：学习者声明 -----------------------------------------

    /** TC-C6：存在 override → 掌握，来源 learner。 */
    @Test
    void overrideMarksLearnerSource() {
        LearningProgress progress = one(C);
        progress.getLearnerMasteryOverrides().put("k1", new LearnerMasteryOverride("k1", ""));
        KnowledgePoint point = firstKp(progress);

        assertThat(MasteryPolicy.isMastered(progress, point)).isTrue();
        assertThat(MasteryPolicy.masterySource(progress, point)).isEqualTo("learner");
    }

    /** TC-C7：声明不写分数（INV-01）。 */
    @Test
    void overrideDoesNotWriteScore() {
        LearningProgress progress = one(M);
        progress.getMasteryLevels().put("k1", 0.1);
        progress.getLearnerMasteryOverrides().put("k1", new LearnerMasteryOverride("k1", ""));
        KnowledgePoint point = firstKp(progress);

        assertThat(MasteryPolicy.isMastered(progress, point)).isTrue();
        assertThat(progress.getMasteryLevels().get("k1")).isEqualTo(0.1);
        assertThat(MasteryPolicy.isAssessedMastered(progress, point)).isFalse();
    }

    /** TC-C8：声明不产生证据（INV-15）。 */
    @Test
    void overrideAddsNoEvidence() {
        LearningProgress progress = one(C);
        progress.getLearnerMasteryOverrides().put("k1", new LearnerMasteryOverride("k1", ""));

        assertThat(progress.getLearningEvidence()).isEmpty();
    }

    // -- TC-C9 / C10 / C11：推进 ---------------------------------------------

    /** TC-C9：返回第一个未掌握的知识点。 */
    @Test
    void nextObjectiveFirstEligible() {
        LearningProgress progress = progress(List.of(module("m1", "M1", 0,
                List.of(kp("k1", "first", M), kp("k2", "second", M)))));
        progress.getMasteryLevels().put("k1", 1.0);

        assertThat(MasteryPolicy.nextObjective(progress).knowledgePointId()).isEqualTo("k2");
    }

    /** TC-C10：全部掌握 → complete。 */
    @Test
    void nextObjectiveComplete() {
        LearningProgress progress = one(M);
        progress.getMasteryLevels().put("k1", 1.0);

        assertThat(MasteryPolicy.nextObjective(progress).action()).isEqualTo("complete");
    }

    /** TC-C11：存在待答题 → answer_pending 优先。 */
    @Test
    void nextObjectiveAnswerPending() {
        LearningProgress progress = one(M);
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q1");
        pending.setKnowledgePointId("k1");
        pending.setPrompt("?");
        progress.setPendingQuestion(pending);

        NextStep step = MasteryPolicy.nextObjective(progress);
        assertThat(step.action()).isEqualTo("answer_pending");
        assertThat(step.knowledgePointId()).isEqualTo("k1");
        assertThat(step.pendingPrompt()).isEqualTo("?");
    }

    // -- TC-C12 / C13 / C14：派生状态 ----------------------------------------

    /** TC-C12：作废的作答不计账（INV-14）。 */
    @Test
    void voidedAttemptIgnored() {
        LearningProgress progress = one(M);
        QuizAttempt correct = new QuizAttempt();
        correct.setQuestionId("q1");
        correct.setKnowledgePointId("k1");
        correct.setCorrect(true);

        QuizAttempt voided = new QuizAttempt();
        voided.setQuestionId("q2");
        voided.setKnowledgePointId("k1");
        voided.setCorrect(false);
        voided.setVoided(true);

        progress.setQuizAttempts(List.of(correct, voided));

        assertThat(MasteryPolicy.activeAttempts(progress, "k1")).hasSize(1);
    }

    /** TC-C13：三种状态派生各一例。 */
    @Test
    void objectiveStatusBranches() {
        LearningProgress progress = one(M);
        KnowledgePoint point = firstKp(progress);
        assertThat(MasteryPolicy.objectiveStatus(progress, point)).isEqualTo("new");

        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuestionId("q");
        attempt.setKnowledgePointId("k1");
        attempt.setCorrect(false);
        progress.setQuizAttempts(List.of(attempt));
        assertThat(MasteryPolicy.objectiveStatus(progress, point)).isEqualTo("learning");

        progress.getMasteryLevels().put("k1", 1.0);
        assertThat(MasteryPolicy.objectiveStatus(progress, point)).isEqualTo("mastered");
    }

    /** TC-C14：map_summary 与门槛判定一致。 */
    @Test
    void mapSummaryMatchesGate() {
        LearningProgress progress = one(M);
        progress.getMasteryLevels().put("k1", 0.95);

        Map<String, Object> entry = MasteryPolicy.mapSummary(progress).get("k1");
        assertThat(entry.get("status")).isEqualTo("mastered");
        assertThat(entry.get("source")).isEqualTo("system");
        assertThat(entry.get("type")).isEqualTo("memory");
        assertThat(entry.get("grounding")).isEqualTo("grounded");
    }

    /** TC-C15：已延后的知识点被跳过，且不算掌握。 */
    @Test
    void deferredSkipped() {
        LearningProgress progress = progress(List.of(module("m1", "M1", 0,
                List.of(kp("k1", "first", M), kp("k2", "second", M)))));
        progress.getDeferredObjectives().put("k1",
                new com.masterypath.domain.model.DeferredObjective("k1", ""));

        assertThat(MasteryPolicy.nextObjective(progress).knowledgePointId()).isEqualTo("k2");
        assertThat(MasteryPolicy.isMastered(progress, firstKp(progress))).isFalse();
    }

    /** TC-C16：类型只读（INV-06）。 */
    @Test
    void typeIsReadOnly() {
        LearningProgress progress = one(C);
        KnowledgePoint point = firstKp(progress);

        assertThatThrownBy(() -> MasteryPolicy.requireTypeUnchanged(point, D))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("read-only");

        // 同类型应当放行
        MasteryPolicy.requireTypeUnchanged(point, C);
    }

    // -- TC-F1：模块阈值不推进 -----------------------------------------------

    /** TC-F1：模块 display_target 不参与推进（INV-18）。 */
    @Test
    void displayTargetNotGate() {
        LearningProgress progress = progress(List.of(module("m1", "M1", 0,
                List.of(kp("k1", "first", M)), "", 0.9)));
        progress.getMasteryLevels().put("k1", 1.0);

        assertThat(MasteryPolicy.nextObjective(progress).action()).isEqualTo("complete");
    }

    // -- TC-F6 / F7 / F8：前置硬门控 -----------------------------------------

    /** TC-F6：前置未掌握 → 返回前置而不是后继（INV-27）。 */
    @Test
    void prerequisiteHardGate() {
        LearningProgress progress = progress(List.of(module("m1", "M1", 0, List.of(
                kp("k1", "implement", P),
                kp("k2", "design", D, "m1", List.of("k1"), List.of("s1"))))));

        assertThat(MasteryPolicy.nextObjective(progress).knowledgePointId()).isEqualTo("k1");
    }

    /** TC-F7：前置已掌握但 design 未定性通过 → 未掌握，且成为下一个目标。 */
    @Test
    void prerequisitePassedButNoQualitative() {
        LearningProgress progress = progress(List.of(module("m1", "M1", 0, List.of(
                kp("k1", "implement", P),
                kp("k2", "design", D, "m1", List.of("k1"), List.of("s1"))))));
        progress.getMasteryLevels().put("k1", 1.0);

        KnowledgePoint design = progress.getModules().get(0).getKnowledgePoints().get(1);
        assertThat(MasteryPolicy.isMastered(progress, design)).isFalse();
        assertThat(MasteryPolicy.nextObjective(progress).knowledgePointId()).isEqualTo("k2");
    }

    /** TC-F8：前置掌握 + 定性通过 → 两条件相与成立。 */
    @Test
    void prerequisiteAndQualitative() {
        LearningProgress progress = progress(List.of(module("m1", "M1", 0, List.of(
                kp("k1", "implement", P),
                kp("k2", "design", D, "m1", List.of("k1"), List.of("s1"))))));
        progress.getMasteryLevels().put("k1", 1.0);
        progress.getQualitativeMastery().put("k2", true);

        KnowledgePoint design = progress.getModules().get(0).getKnowledgePoints().get(1);
        assertThat(MasteryPolicy.isMastered(progress, design)).isTrue();
    }

    /** 悬空前置视为不满足（fail-closed）。 */
    @Test
    void danglingPrerequisiteFailsClosed() {
        LearningProgress progress = progress(List.of(module("m1", "M1", 0,
                List.of(kp("k2", "design", D, "m1", List.of("ghost"), List.of("s1"))))));

        assertThat(MasteryPolicy.eligible(progress,
                progress.getModules().get(0).getKnowledgePoints().get(0))).isFalse();
        assertThat(MasteryPolicy.nextObjective(progress).action()).isEqualTo("complete");
    }
}
