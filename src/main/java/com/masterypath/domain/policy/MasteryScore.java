package com.masterypath.domain.policy;

import com.masterypath.domain.model.QuizAttempt;

import java.util.ArrayList;
import java.util.List;

/**
 * 掌握度分数：近期加权正确率（ADR-007）。
 *
 * <p>纯函数。定量门槛（{@code memory}/{@code procedure}）读取本值；
 * {@code concept}/{@code design} 不读它（走定性评估）。
 *
 * <p><b>衰减方向</b>：最新一次作答权重为 1.0，越早权重按 {@code 0.6^rank} 递减。
 * 这样「早期答错、近期答对」的学习者不会被历史错误长期压制。
 * 方向写反会导致刚学会的人永远达不到门槛。
 */
public final class MasteryScore {

    /** 越早的作答衰减越快；与参考实现的 {@code _DECAY} 一致。 */
    static final double DECAY = 0.6;

    private MasteryScore() {
    }

    /**
     * 计算近期加权正确率。
     *
     * @param attempts 该知识点的全部作答（内部会跳过 {@code voided}）
     * @return {@code 0.0}～{@code 1.0}；无有效作答时返回 {@code 0.0}
     */
    public static double compute(List<QuizAttempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return 0.0;
        }
        List<QuizAttempt> active = new ArrayList<>();
        for (QuizAttempt attempt : attempts) {
            if (!attempt.isVoided()) {
                active.add(attempt);
            }
        }
        if (active.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        double weightSum = 0.0;
        int count = active.size();
        for (int index = 0; index < count; index++) {
            // index 0 是最早的一条；最新一条 rank=0，权重 1.0
            int rank = count - 1 - index;
            double weight = Math.pow(DECAY, rank);
            total += weight * (active.get(index).isCorrect() ? 1.0 : 0.0);
            weightSum += weight;
        }
        return weightSum == 0.0 ? 0.0 : total / weightSum;
    }
}
