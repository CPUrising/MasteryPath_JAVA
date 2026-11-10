package com.masterypath.domain.policy;

import com.masterypath.domain.model.QuizAttempt;

import java.util.ArrayList;
import java.util.List;

/**
 * 掌握度分数（逐字复刻 DeepTutor {@code learning/mastery.py::compute_mastery}）。
 *
 * <p>最近 5 次作答按 {@code (0.5, 0.7, 0.85, 0.95, 1.0)} 加权（越新越大），
 * 并施加置信上限：只有 1 次作答时分数不超过 0.5，只有 2 次时不超过 0.8。
 * 因此一次或两次正确都不足以跨过 0.9 门槛。
 *
 * <p>只统计 {@code independent=true} 且未作废的作答。
 */
public final class MasteryScore {

    /** 最近 5 次作答的权重（最旧 → 最新）。 */
    static final double[] RECENCY_WEIGHTS = {0.5, 0.7, 0.85, 0.95, 1.0};

    private MasteryScore() {
    }

    /**
     * 计算近期加权正确率。
     *
     * @param attempts 该知识点的全部作答（内部会跳过 {@code voided} 与非独立作答）
     * @return {@code 0.0}～{@code 1.0}；无有效作答时返回 {@code 0.0}
     */
    public static double compute(List<QuizAttempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return 0.0;
        }
        List<QuizAttempt> active = new ArrayList<>();
        for (QuizAttempt attempt : attempts) {
            if (!attempt.isVoided() && attempt.isIndependent()) {
                active.add(attempt);
            }
        }
        if (active.isEmpty()) {
            return 0.0;
        }

        int recent = Math.min(RECENCY_WEIGHTS.length, active.size());
        double total = 0.0;
        double weightSum = 0.0;
        for (int offset = 0; offset < recent; offset++) {
            // offset 0 对应最近一次；权重从数组末尾往前取。
            double weight = RECENCY_WEIGHTS[RECENCY_WEIGHTS.length - 1 - offset];
            QuizAttempt attempt = active.get(active.size() - 1 - offset);
            total += weight * (attempt.isCorrect() ? 1.0 : 0.0);
            weightSum += weight;
        }
        double score = weightSum == 0.0 ? 0.0 : total / weightSum;
        double cap = switch (recent) {
            case 1 -> 0.5;
            case 2 -> 0.8;
            default -> 1.0;
        };
        return Math.min(score, cap);
    }
}
