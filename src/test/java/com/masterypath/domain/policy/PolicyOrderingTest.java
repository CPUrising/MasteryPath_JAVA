package com.masterypath.domain.policy;

import static com.masterypath.domain.policy.PolicyFixtures.kp;
import static com.masterypath.domain.policy.PolicyFixtures.module;
import static com.masterypath.domain.policy.PolicyFixtures.progress;
import static org.assertj.core.api.Assertions.assertThat;

import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.domain.model.RepetitionState;
import com.masterypath.domain.model.ReviewTask;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code nextObjective} 的优先级顺序。
 *
 * <p>顺序即语义：调换任何一个分支都会让某些错误静默通过
 * （例如有待答题时仍返回 practice，或未掌握目标存在时先去复习）。
 */
class PolicyOrderingTest {

    private static final double NOW = 1_800_000_000.0;

    /** 待答题优先级最高，即使存在未掌握目标。 */
    @Test
    void pendingQuestionBeatsUnmasteredObjective() {
        LearningProgress progress = progress(List.of(module("m1", "M1", 0,
                List.of(kp("k1", "first", KnowledgeType.MEMORY)))));
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q1");
        pending.setKnowledgePointId("k1");
        progress.setPendingQuestion(pending);

        assertThat(MasteryPolicy.nextObjective(progress, NOW).action()).isEqualTo("answer_pending");
    }

    /** 未掌握目标优先于到期复习。 */
    @Test
    void unmasteredObjectiveBeatsDueReview() {
        LearningProgress progress = progress(List.of(module("m1", "M1", 0,
                List.of(kp("k1", "first", KnowledgeType.MEMORY),
                        kp("k2", "second", KnowledgeType.MEMORY)))));
        progress.getMasteryLevels().put("k1", 1.0);
        progress.setReviewQueue(List.of(reviewTask("k1", NOW - 100)));

        NextStep step = MasteryPolicy.nextObjective(progress, NOW);
        assertThat(step.action()).isEqualTo("practice");
        assertThat(step.knowledgePointId()).isEqualTo("k2");
    }

    /** 全部掌握后，到期复习优先于 complete。 */
    @Test
    void dueReviewBeatsComplete() {
        LearningProgress progress = progress(List.of(module("m1", "M1", 0,
                List.of(kp("k1", "first", KnowledgeType.MEMORY)))));
        progress.getMasteryLevels().put("k1", 1.0);
        progress.setReviewQueue(List.of(reviewTask("k1", NOW - 100)));

        NextStep step = MasteryPolicy.nextObjective(progress, NOW);
        assertThat(step.action()).isEqualTo("review");
        assertThat(step.knowledgePointId()).isEqualTo("k1");
    }

    /** 未到期复习不算：仍返回 complete。 */
    @Test
    void futureReviewDoesNotCount() {
        LearningProgress progress = progress(List.of(module("m1", "M1", 0,
                List.of(kp("k1", "first", KnowledgeType.MEMORY)))));
        progress.getMasteryLevels().put("k1", 1.0);
        progress.setReviewQueue(List.of(reviewTask("k1", NOW + 100)));

        assertThat(MasteryPolicy.nextObjective(progress, NOW).action()).isEqualTo("complete");
    }

    /** 定量类型返回 practice，定性类型返回 assess。 */
    @Test
    void actionReflectsType() {
        LearningProgress memory = progress(List.of(module("m1", "M1", 0,
                List.of(kp("k1", "first", KnowledgeType.MEMORY)))));
        assertThat(MasteryPolicy.nextObjective(memory, NOW).action()).isEqualTo("practice");

        LearningProgress concept = progress(List.of(module("m1", "M1", 0,
                List.of(kp("k1", "first", KnowledgeType.CONCEPT)))));
        assertThat(MasteryPolicy.nextObjective(concept, NOW).action()).isEqualTo("assess");
    }

    /** 模块按 order 升序，而不是列表顺序。 */
    @Test
    void modulesOrderedByOrderField() {
        LearningProgress progress = progress(List.of(
                module("m2", "M2", 1, List.of(kp("k2", "second", KnowledgeType.MEMORY))),
                module("m1", "M1", 0, List.of(kp("k1", "first", KnowledgeType.MEMORY)))));

        assertThat(MasteryPolicy.nextObjective(progress, NOW).knowledgePointId()).isEqualTo("k1");
    }

    /** 到期复习按遗忘风险降序。 */
    @Test
    void dueReviewsOrderedByRisk() {
        LearningProgress progress = progress(List.of(module("m1", "M1", 0,
                List.of(kp("k1", "alpha", KnowledgeType.MEMORY),
                        kp("k2", "beta", KnowledgeType.MEMORY)))));
        ReviewTask low = reviewTask("k1", NOW - 100);
        low.setForgettingRisk(0.2);
        ReviewTask high = reviewTask("k2", NOW - 50);
        high.setForgettingRisk(0.9);
        progress.setReviewQueue(List.of(low, high));

        List<ReviewTask> due = MasteryPolicy.dueReviews(progress, NOW);
        assertThat(due).extracting(ReviewTask::getKnowledgePointId).containsExactly("k2", "k1");
    }

    private static ReviewTask reviewTask(String kpId, double dueAt) {
        ReviewTask task = new ReviewTask();
        task.setId("r-" + kpId);
        task.setKnowledgePointId(kpId);
        task.setKnowledgeType(KnowledgeType.MEMORY);
        task.setDueAt(dueAt);
        RepetitionState state = new RepetitionState();
        state.setNextReviewAt(dueAt);
        task.setState(state);
        return task;
    }
}
