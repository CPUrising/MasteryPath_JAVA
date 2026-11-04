package com.masterypath.domain.schedule;

import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.RepetitionState;
import com.masterypath.domain.model.ReviewTask;

/**
 * 间隔复习调度（SM-2，{@code ADR-009} / spec 04）。
 *
 * <p><b>纯算术</b>：不读库、不发事件、不取当前时间（时间一律由调用方注入）。
 * 单一调度器按 {@code provenance} 播种（{@code ADR-002}）：声明掌握的首间隔约为评估掌握的一半。
 *
 * <p>{@code scheduledAfterFailure} 标记<b>锚点</b>（{@code nextReviewAt}）是否由失败响应产生。
 * 它跟随锚点：正式复习成功会设置新锚点并清零；会话内练习只改 {@code lastReviewAt}，
 * 不动锚点也不动该标志（{@code INV-29/30}）。
 */
public final class Sm2Scheduler {

    public static final double SECONDS_PER_DAY = 86400.0;

    /** 重学步：10 分钟。 */
    public static final double RELEARNING_SECONDS = 600.0;

    public static final double DEFAULT_EF = 2.5;

    public static final double MIN_EF = 1.3;

    /** 声明掌握的首间隔约为系统轨的一半。 */
    public static final double LEARNER_FIRST_FACTOR = 0.5;

    /** FSRS 展示参数；SM-2 不消费它（{@code INV-31}）。 */
    private static final double DEFAULT_DESIRED_RETENTION = 0.9;

    private Sm2Scheduler() {
    }

    /** 把判分结果映射到 SM-2 的 q（0..5）。 */
    public static int qualityFromResult(String result, int hintsUsed) {
        if ("correct".equals(result) && hintsUsed == 0) {
            return 5;
        }
        if ("correct".equals(result)) {
            return 4;
        }
        if ("partial".equals(result)) {
            return 3;
        }
        return 0;
    }

    public static int qualityFromResult(String result) {
        return qualityFromResult(result, 0);
    }

    /** EF' = EF + (0.1 - (5-q)*(0.08 + (5-q)*0.02))，下限 {@link #MIN_EF}。 */
    public static double updateEase(double easeFactor, int quality) {
        double adjusted = easeFactor
                + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
        return Math.max(MIN_EF, adjusted);
    }

    /**
     * SM-2 间隔：1、6，之后是上一次间隔乘以 EF。
     *
     * <p><b>舍入语义</b>：参考实现用 Python 的 {@code round()}，对 {@code x.5} 走
     * <b>银行家舍入</b>（向偶数）。此处必须用 {@link Math#rint}，{@link Math#round} 会差 1 天。
     */
    public static int intervalDays(int index, double easeFactor) {
        if (index <= 1) {
            return 1;
        }
        if (index == 2) {
            return 6;
        }
        double days = 6.0;
        for (int step = 3; step <= index; step++) {
            days = Math.rint(days * easeFactor);
        }
        return (int) days;
    }

    public static RepetitionState initialState(String provenance, double now) {
        return initialState(provenance, now, DEFAULT_DESIRED_RETENTION);
    }

    /**
     * 播种刚掌握的目标（单调度器，按来源分轨）。
     *
     * <p>非法或空来源归一到 {@code system}——参考实现用
     * {@code provenance if provenance in ("system","learner") else "system"}。
     */
    public static RepetitionState initialState(String provenance, double now,
                                               double desiredRetention) {
        boolean learner = "learner".equals(provenance);
        double factor = learner ? LEARNER_FIRST_FACTOR : 1.0;
        double first = intervalDays(1, DEFAULT_EF) * factor;
        RepetitionState state = new RepetitionState();
        state.setIntervalIndex(1);
        state.setEaseFactor(DEFAULT_EF);
        state.setNextReviewAt(now + first * SECONDS_PER_DAY);
        state.setLastScheduledAt(now);
        state.setProvenance(learner ? "learner" : "system");
        state.setDesiredRetention(desiredRetention);
        return state;
    }

    /**
     * 一次正式复习后的重排（<b>就地修改</b>并返回同一对象）。
     *
     * <p>{@code quality < 3} 视为失败：回到重学步、累计遗忘次数、标记锚点由失败产生。
     */
    public static RepetitionState applyReview(RepetitionState state, String result,
                                              int hintsUsed, double now) {
        int quality = qualityFromResult(result, hintsUsed);
        state.setEaseFactor(updateEase(state.getEaseFactor(), quality));
        state.setReviewCount(state.getReviewCount() + 1);
        state.setLastReviewAt(now);
        if (quality < 3) {
            state.setIntervalIndex(0);
            state.setConsecutiveCorrect(0);
            state.setConsecutiveWrong(state.getConsecutiveWrong() + 1);
            state.setLapseCount(state.getLapseCount() + 1);
            state.setNextReviewAt(now + RELEARNING_SECONDS);
            state.setLastScheduledAt(now);
            state.setScheduledAfterFailure(true);
        } else {
            state.setIntervalIndex(state.getIntervalIndex() + 1);
            state.setConsecutiveCorrect(state.getConsecutiveCorrect() + 1);
            state.setConsecutiveWrong(0);
            state.setNextReviewAt(now
                    + intervalDays(state.getIntervalIndex(), state.getEaseFactor()) * SECONDS_PER_DAY);
            state.setLastScheduledAt(now);
            state.setScheduledAfterFailure(false);
        }
        return state;
    }

    /** 会话内练习：只更新 {@code lastReviewAt}，不动锚点（{@code INV-29}）。 */
    public static RepetitionState touchLastReview(RepetitionState state, double now) {
        state.setLastReviewAt(now);
        return state;
    }

    public static ReviewTask buildReviewTask(KnowledgePoint kp, RepetitionState state,
                                             String reason, String evidenceSource,
                                             String evidenceId) {
        ReviewTask task = new ReviewTask();
        task.setId("rev:" + kp.getId());
        task.setKnowledgePointId(kp.getId());
        task.setKnowledgeType(kp.getType());
        task.setDueAt(state.getNextReviewAt());
        task.setState(state);
        task.setReason(reason);
        task.setEvidenceSource(evidenceSource);
        task.setEvidenceId(evidenceId);
        return task;
    }

    /**
     * 每个知识点至多一条复习任务：先移除同 KP 的旧项，再把新项追加到队尾。
     *
     * <p>「追加到队尾」而非原位替换是参考实现原样行为；排序由
     * {@code MasteryPolicy.dueReviews} 负责，不依赖队列顺序。
     */
    public static ReviewTask upsertReviewTask(LearningProgress progress, KnowledgePoint kp,
                                              RepetitionState state, String evidenceSource,
                                              String evidenceId) {
        ReviewTask task = buildReviewTask(kp, state, "spaced review", evidenceSource, evidenceId);
        progress.getReviewQueue().removeIf(
                existing -> kp.getId().equals(existing.getKnowledgePointId()));
        progress.getReviewQueue().add(task);
        return task;
    }
}
