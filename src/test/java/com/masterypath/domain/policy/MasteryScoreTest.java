package com.masterypath.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.masterypath.domain.model.QuizAttempt;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 近期加权正确率（{@code learning/mastery.py}）。
 *
 * <p>最关键的一条是「早期错、近期对」必须能超过 0.9——它证明衰减方向正确。
 * 方向写反会让刚学会的学习者永远达不到定量门槛。
 */
class MasteryScoreTest {

    private static QuizAttempt attempt(String id, boolean correct, boolean voided) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuestionId(id);
        attempt.setKnowledgePointId("k1");
        attempt.setCorrect(correct);
        attempt.setVoided(voided);
        return attempt;
    }

    @Test
    void emptyReturnsZero() {
        assertThat(MasteryScore.compute(List.of())).isZero();
        assertThat(MasteryScore.compute(null)).isZero();
    }

    @Test
    void allCorrectIsOne() {
        assertThat(MasteryScore.compute(List.of(
                attempt("q1", true, false),
                attempt("q2", true, false)))).isEqualTo(1.0);
    }

    @Test
    void allWrongIsZero() {
        assertThat(MasteryScore.compute(List.of(
                attempt("q1", false, false),
                attempt("q2", false, false)))).isZero();
    }

    /** 衰减方向：最新一条权重最高。 */
    @Test
    void recentCorrectOutweighsEarlyMistakes() {
        // 早期错 3 次，最近对 1 次
        double score = MasteryScore.compute(List.of(
                attempt("q1", false, false),
                attempt("q2", false, false),
                attempt("q3", false, false),
                attempt("q4", true, false)));

        // 权重：0.6^3, 0.6^2, 0.6^1, 0.6^0 = 0.216, 0.36, 0.6, 1.0
        // 分子 = 1.0；分母 = 2.176
        assertThat(score).isCloseTo(1.0 / 2.176, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(score).isGreaterThan(0.45);
    }

    /** 若方向写反（早期权重最高），同样序列会得到明显更低的值。 */
    @Test
    void decayDirectionIsNotReversed() {
        double recencyWeighted = MasteryScore.compute(List.of(
                attempt("q1", false, false),
                attempt("q2", true, false)));

        // 正确方向：分子 = 1.0（最新），分母 = 0.6 + 1.0 = 1.6 → 0.625
        // 反向：分子 = 0.6，分母 = 1.6 → 0.375
        assertThat(recencyWeighted).isCloseTo(1.0 / 1.6, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(recencyWeighted).isGreaterThan(0.5);
    }

    /** 作废记录不参与计算（INV-14）。 */
    @Test
    void voidedAttemptsExcluded() {
        double withVoided = MasteryScore.compute(List.of(
                attempt("q1", false, true),
                attempt("q2", true, false)));

        assertThat(withVoided).isEqualTo(1.0);
    }

    /** 全部作废 → 0.0（无有效证据）。 */
    @Test
    void allVoidedIsZero() {
        assertThat(MasteryScore.compute(List.of(
                attempt("q1", true, true)))).isZero();
    }

    /** 连续答对足够多次后能跨过 0.9 门槛。 */
    @Test
    void sustainedCorrectCrossesGate() {
        double score = MasteryScore.compute(List.of(
                attempt("q1", false, false),
                attempt("q2", true, false),
                attempt("q3", true, false),
                attempt("q4", true, false)));

        // 分子 = 0.216 + 0.36 + 0.6 + 1.0 = 2.176... 其中仅后三条为 1
        // 实际：0.6^3*0 + 0.6^2*1 + 0.6^1*1 + 0.6^0*1 = 0.36+0.6+1.0 = 1.96
        // 分母 = 2.176 → 0.9007
        assertThat(score).isGreaterThanOrEqualTo(0.9);
    }
}
