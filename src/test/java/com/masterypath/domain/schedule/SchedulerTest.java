package com.masterypath.domain.schedule;

import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.RepetitionState;
import com.masterypath.domain.model.ReviewTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * SM-2 纯算术（{@code TC-G1}…{@code TC-G5}、{@code TC-G7}）。
 *
 * <p>对照标准：{@code ../MasteryPath/tests/test_scheduler.py}。
 * 参考实现的 {@code round()} 是<b>银行家舍入</b>，因此本类必须用 {@link Math#rint}；
 * 用 {@link Math#round} 在 {@code x.5} 处会差 1 天，且 TC-G1 的 1/6/15/38/95 抓不住这个差异。
 */
class SchedulerTest {

    private static final double EPS = 1e-9;

    // -- TC-G1 间隔序列 -------------------------------------------------------

    @Test
    @DisplayName("TC-G1：q=5 连续时间隔依次为 1/6/15/38/95 天")
    void intervalSequence() {
        double ef = Sm2Scheduler.DEFAULT_EF;

        assertThat(new int[]{
                Sm2Scheduler.intervalDays(1, ef),
                Sm2Scheduler.intervalDays(2, ef),
                Sm2Scheduler.intervalDays(3, ef),
                Sm2Scheduler.intervalDays(4, ef),
                Sm2Scheduler.intervalDays(5, ef)})
                .containsExactly(1, 6, 15, 38, 95);
    }

    @Test
    @DisplayName("TC-G1：更长序列与参考实现逐位一致（EF=2.5）")
    void longSequence() {
        assertThat(new int[]{
                Sm2Scheduler.intervalDays(6, 2.5),
                Sm2Scheduler.intervalDays(7, 2.5),
                Sm2Scheduler.intervalDays(8, 2.5)})
                .containsExactly(238, 595, 1488);
    }

    @Test
    @DisplayName("TC-G1：非默认 EF 的序列与参考实现一致")
    void sequenceWithOtherEaseFactors() {
        assertThat(new int[]{
                Sm2Scheduler.intervalDays(3, 2.36),
                Sm2Scheduler.intervalDays(4, 2.36),
                Sm2Scheduler.intervalDays(5, 2.36)})
                .containsExactly(14, 33, 78);
        assertThat(new int[]{
                Sm2Scheduler.intervalDays(3, 1.3),
                Sm2Scheduler.intervalDays(4, 1.3),
                Sm2Scheduler.intervalDays(5, 1.3)})
                .containsExactly(8, 10, 13);
    }

    @Test
    @DisplayName("TC-G1：x.5 走银行家舍入（向偶数），不是四舍五入")
    void roundsHalfToEven() {
        // 6 * 2.75 = 16.5 → Python round 得 16；Math.round 会得 17。
        assertThat(Sm2Scheduler.intervalDays(3, 2.75)).isEqualTo(16);
        // 6 * 2.5 = 15（整值），再 15 * 2.5 = 37.5 → 38（偶数）。
        assertThat(Sm2Scheduler.intervalDays(4, 2.5)).isEqualTo(38);
        // 6 * 1.75 = 10.5 → 10（偶数）；10 * 1.75 = 17.5 → 18（偶数）。
        assertThat(Sm2Scheduler.intervalDays(3, 1.75)).isEqualTo(10);
        assertThat(Sm2Scheduler.intervalDays(4, 1.75)).isEqualTo(18);
    }

    // -- 评分与 EF ------------------------------------------------------------

    @Test
    @DisplayName("评分映射：独立答对 5 / 有提示 4 / partial 3 / 其余 0")
    void qualityMapping() {
        assertThat(Sm2Scheduler.qualityFromResult("correct", 0)).isEqualTo(5);
        assertThat(Sm2Scheduler.qualityFromResult("correct", 1)).isEqualTo(4);
        assertThat(Sm2Scheduler.qualityFromResult("partial", 0)).isEqualTo(3);
        assertThat(Sm2Scheduler.qualityFromResult("incorrect", 0)).isZero();
        assertThat(Sm2Scheduler.qualityFromResult("ungraded", 0)).isZero();
    }

    @Test
    @DisplayName("EF 公式：EF=2.5 时 q=0..5 依次为 1.7/1.96/2.18/2.36/2.5/2.6")
    void easeUpdate() {
        double ef = Sm2Scheduler.DEFAULT_EF;
        assertThat(Sm2Scheduler.updateEase(ef, 0)).isCloseTo(1.7, within(EPS));
        assertThat(Sm2Scheduler.updateEase(ef, 1)).isCloseTo(1.96, within(EPS));
        assertThat(Sm2Scheduler.updateEase(ef, 2)).isCloseTo(2.18, within(EPS));
        assertThat(Sm2Scheduler.updateEase(ef, 3)).isCloseTo(2.36, within(EPS));
        assertThat(Sm2Scheduler.updateEase(ef, 4)).isCloseTo(2.5, within(EPS));
        assertThat(Sm2Scheduler.updateEase(ef, 5)).isCloseTo(2.6, within(EPS));
    }

    @Test
    @DisplayName("EF 下限 1.3：已在下限时不再下降")
    void easeFloor() {
        assertThat(Sm2Scheduler.updateEase(Sm2Scheduler.DEFAULT_EF, 0))
                .isGreaterThanOrEqualTo(Sm2Scheduler.MIN_EF);
        assertThat(Sm2Scheduler.updateEase(Sm2Scheduler.MIN_EF, 0))
                .isEqualTo(Sm2Scheduler.MIN_EF);
    }

    // -- TC-G2 失败进重学步 ---------------------------------------------------

    @Test
    @DisplayName("TC-G2：失败后 next_review_at 落在 1 天内，lapse_count 递增，标志为真")
    void failureRelearning() {
        RepetitionState state = Sm2Scheduler.initialState("system", 1000.0);

        Sm2Scheduler.applyReview(state, "incorrect", 0, 2000.0);

        assertThat(state.isScheduledAfterFailure()).isTrue();
        assertThat(state.getLapseCount()).isEqualTo(1);
        assertThat(state.getIntervalIndex()).isZero();
        assertThat(state.getConsecutiveWrong()).isEqualTo(1);
        assertThat(state.getConsecutiveCorrect()).isZero();
        assertThat(state.getReviewCount()).isEqualTo(1);
        assertThat(state.getLastReviewAt()).isEqualTo(2000.0);
        assertThat(state.getNextReviewAt() - 2000.0)
                .isLessThanOrEqualTo(Sm2Scheduler.SECONDS_PER_DAY);
        assertThat(state.getNextReviewAt() - 2000.0)
                .isEqualTo(Sm2Scheduler.RELEARNING_SECONDS);
    }

    @Test
    @DisplayName("TC-G2：partial 不算失败（q=3），走正常序列")
    void partialIsNotLapse() {
        RepetitionState state = Sm2Scheduler.initialState("system", 1000.0);

        Sm2Scheduler.applyReview(state, "partial", 0, 2000.0);

        assertThat(state.isScheduledAfterFailure()).isFalse();
        assertThat(state.getLapseCount()).isZero();
        assertThat(state.getIntervalIndex()).isEqualTo(2);
        assertThat(state.getConsecutiveCorrect()).isEqualTo(1);
    }

    // -- TC-G3 恢复清标志 -----------------------------------------------------

    @Test
    @DisplayName("TC-G3：失败后正式复习成功，标志清零且间隔回到正常序列")
    void recoveryClearsFlag() {
        RepetitionState state = Sm2Scheduler.initialState("system", 1000.0);
        Sm2Scheduler.applyReview(state, "incorrect", 0, 2000.0);

        Sm2Scheduler.applyReview(state, "correct", 0, 3000.0);

        assertThat(state.isScheduledAfterFailure()).isFalse();
        assertThat(state.getIntervalIndex()).isEqualTo(1);
        assertThat(state.getConsecutiveWrong()).isZero();
        assertThat(state.getLapseCount()).isEqualTo(1);
        double expected = 3000.0
                + Sm2Scheduler.intervalDays(1, state.getEaseFactor()) * Sm2Scheduler.SECONDS_PER_DAY;
        assertThat(state.getNextReviewAt()).isEqualTo(expected);
    }

    // -- TC-G4 会话内只 touch -------------------------------------------------

    @Test
    @DisplayName("TC-G4：会话内练习只改 last_review_at，锚点与失败标志不变")
    void inSessionTouchOnly() {
        RepetitionState state = Sm2Scheduler.initialState("system", 1000.0);
        double anchor = state.getNextReviewAt();

        RepetitionState returned = Sm2Scheduler.touchLastReview(state, 5000.0);

        assertThat(returned).isSameAs(state);
        assertThat(state.getNextReviewAt()).isEqualTo(anchor);
        assertThat(state.getLastReviewAt()).isEqualTo(5000.0);
        assertThat(state.isScheduledAfterFailure()).isFalse();
        assertThat(state.getReviewCount()).isZero();
    }

    // -- TC-G5 双轨播种 -------------------------------------------------------

    @Test
    @DisplayName("TC-G5：声明轨首次到期时间约为系统轨的一半，比值落在 [0.3, 0.7]")
    void learnerTrackRatio() {
        RepetitionState system = Sm2Scheduler.initialState("system", 0.0);
        RepetitionState learner = Sm2Scheduler.initialState("learner", 0.0);

        double ratio = learner.getNextReviewAt() / system.getNextReviewAt();

        assertThat(ratio).isBetween(0.3, 0.7);
        assertThat(ratio).isCloseTo(Sm2Scheduler.LEARNER_FIRST_FACTOR, within(EPS));
    }

    @Test
    @DisplayName("播种：interval_index 为 1，EF 为 2.5，last_scheduled_at 等于 now")
    void initialStateFields() {
        RepetitionState state = Sm2Scheduler.initialState("system", 1234.5);

        assertThat(state.getIntervalIndex()).isEqualTo(1);
        assertThat(state.getEaseFactor()).isEqualTo(Sm2Scheduler.DEFAULT_EF);
        assertThat(state.getLastScheduledAt()).isEqualTo(1234.5);
        assertThat(state.getNextReviewAt()).isEqualTo(1234.5 + Sm2Scheduler.SECONDS_PER_DAY);
        assertThat(state.getProvenance()).isEqualTo("system");
        assertThat(state.isScheduledAfterFailure()).isFalse();
    }

    @Test
    @DisplayName("播种：空来源或未知来源归一到 system")
    void provenanceNormalisesToSystem() {
        assertThat(Sm2Scheduler.initialState("", 0.0).getProvenance()).isEqualTo("system");
        assertThat(Sm2Scheduler.initialState(null, 0.0).getProvenance()).isEqualTo("system");
        assertThat(Sm2Scheduler.initialState("teacher", 0.0).getProvenance()).isEqualTo("system");
        assertThat(Sm2Scheduler.initialState("learner", 0.0).getProvenance()).isEqualTo("learner");
    }

    // -- TC-G7 场景不追溯 -----------------------------------------------------

    @Test
    @DisplayName("TC-G7：SM-2 计算不消费 desired_retention（FSRS 展示参数，INV-31）")
    void desiredRetentionNotConsumed() {
        RepetitionState low = Sm2Scheduler.initialState("system", 1000.0, 0.7);
        RepetitionState high = Sm2Scheduler.initialState("system", 1000.0, 0.99);

        Sm2Scheduler.applyReview(low, "correct", 0, 2000.0);
        Sm2Scheduler.applyReview(high, "correct", 0, 2000.0);

        assertThat(low.getNextReviewAt()).isEqualTo(high.getNextReviewAt());
        assertThat(low.getIntervalIndex()).isEqualTo(high.getIntervalIndex());
        assertThat(low.getEaseFactor()).isEqualTo(high.getEaseFactor());
    }

    @Test
    @DisplayName("TC-G7：修改聚合的 desired_retention 不改已有锚点")
    void changingRetentionKeepsAnchor() {
        RepetitionState state = Sm2Scheduler.initialState("system", 1000.0);
        LearningProgress progress = new LearningProgress("p");
        progress.getRepetitionStates().put("k1", state);
        double anchor = state.getNextReviewAt();

        progress.setDesiredRetention(0.95);

        assertThat(progress.getRepetitionStates().get("k1").getNextReviewAt()).isEqualTo(anchor);
    }

    // -- 队列 ----------------------------------------------------------------

    @Test
    @DisplayName("复习任务 id 为 rev:<kpId>，到期时间取状态锚点")
    void buildReviewTaskFields() {
        KnowledgePoint kp = point("k1", "alpha", KnowledgeType.MEMORY);
        RepetitionState state = Sm2Scheduler.initialState("system", 1000.0);

        ReviewTask task = Sm2Scheduler.buildReviewTask(kp, state, "spaced review", "mastery_path", "e1");

        assertThat(task.getId()).isEqualTo("rev:k1");
        assertThat(task.getKnowledgePointId()).isEqualTo("k1");
        assertThat(task.getKnowledgeType()).isEqualTo(KnowledgeType.MEMORY);
        assertThat(task.getDueAt()).isEqualTo(state.getNextReviewAt());
        assertThat(task.getReason()).isEqualTo("spaced review");
        assertThat(task.getEvidenceSource()).isEqualTo("mastery_path");
        assertThat(task.getEvidenceId()).isEqualTo("e1");
        assertThat(task.getState()).isSameAs(state);
    }

    @Test
    @DisplayName("upsert 先移除同 KP 旧项再追加；其他 KP 原样保留")
    void upsertReplacesSamePointOnly() {
        LearningProgress progress = new LearningProgress("p");
        KnowledgePoint alpha = point("k1", "alpha", KnowledgeType.MEMORY);
        KnowledgePoint beta = point("k2", "beta", KnowledgeType.CONCEPT);
        RepetitionState alphaState = Sm2Scheduler.initialState("system", 1000.0);
        RepetitionState betaState = Sm2Scheduler.initialState("system", 1000.0);

        Sm2Scheduler.upsertReviewTask(progress, alpha, alphaState, "mastery_path", "e1");
        Sm2Scheduler.upsertReviewTask(progress, beta, betaState, "mastery_path", "e2");
        Sm2Scheduler.upsertReviewTask(progress, alpha, alphaState, "mastery_path", "e3");

        List<ReviewTask> queue = progress.getReviewQueue();
        assertThat(queue).hasSize(2);
        assertThat(queue).extracting(ReviewTask::getKnowledgePointId)
                .containsExactly("k2", "k1");
        assertThat(queue.get(1).getEvidenceId()).isEqualTo("e3");
    }

    private static KnowledgePoint point(String id, String name, KnowledgeType type) {
        return new KnowledgePoint(id, name, type, "m1");
    }
}
