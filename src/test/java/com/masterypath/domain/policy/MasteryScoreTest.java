package com.masterypath.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import com.masterypath.domain.model.QuizAttempt;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 掌握度分数（逐字复刻 DeepTutor {@code learning/mastery.py}）。
 *
 * <p>核心语义：最近 5 次加权、1 次上限 0.5、2 次上限 0.8；
 * 只有独立且未作废的作答计入。
 */
class MasteryScoreTest {

    private static QuizAttempt attempt(String id, boolean correct, boolean independent) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuestionId(id);
        attempt.setKnowledgePointId("k1");
        attempt.setCorrect(correct);
        attempt.setIndependent(independent);
        return attempt;
    }

    @Test
    void emptyReturnsZero() {
        assertThat(MasteryScore.compute(List.of())).isZero();
        assertThat(MasteryScore.compute(null)).isZero();
    }

    @Test
    @DisplayName("一次正确被置信上限压到 0.5")
    void singleCorrectIsCapped() {
        assertThat(MasteryScore.compute(List.of(attempt("q1", true, true)))).isEqualTo(0.5);
    }

    @Test
    @DisplayName("两次正确被置信上限压到 0.8")
    void twoCorrectAreCapped() {
        assertThat(MasteryScore.compute(List.of(
                attempt("q1", true, true),
                attempt("q2", true, true)))).isEqualTo(0.8);
    }

    @Test
    @DisplayName("三次正确达到 1.0")
    void threeCorrectReachOne() {
        assertThat(MasteryScore.compute(List.of(
                attempt("q1", true, true),
                attempt("q2", true, true),
                attempt("q3", true, true)))).isEqualTo(1.0);
    }

    @Test
    void allWrongIsZero() {
        assertThat(MasteryScore.compute(List.of(
                attempt("q1", false, true),
                attempt("q2", false, true)))).isZero();
    }

    @Test
    @DisplayName("最近 5 次权重为 0.5/0.7/0.85/0.95/1.0")
    void recentFiveWeights() {
        // 错、错、错、对、对 → (0.85*0 + 0.95*1 + 1.0*1) / 4.0
        double score = MasteryScore.compute(List.of(
                attempt("q1", false, true),
                attempt("q2", false, true),
                attempt("q3", false, true),
                attempt("q4", true, true),
                attempt("q5", true, true)));

        assertThat(score).isCloseTo(1.95 / 4.0, offset(1e-9));
    }

    @Test
    @DisplayName("超过 5 次时只取最近 5 次")
    void onlyLastFiveCount() {
        // 最早的 correct 被丢弃，剩下 错、错、错、对、对
        double score = MasteryScore.compute(List.of(
                attempt("q0", true, true),
                attempt("q1", false, true),
                attempt("q2", false, true),
                attempt("q3", false, true),
                attempt("q4", true, true),
                attempt("q5", true, true)));

        assertThat(score).isCloseTo(1.95 / 4.0, offset(1e-9));
    }

    @Test
    @DisplayName("作废记录不参与计算")
    void voidedAttemptsExcluded() {
        QuizAttempt voided = attempt("q1", true, true);
        voided.setVoided(true);

        assertThat(MasteryScore.compute(List.of(
                voided,
                attempt("q2", true, true),
                attempt("q3", true, true),
                attempt("q4", true, true)))).isEqualTo(1.0);
    }

    @Test
    @DisplayName("使用提示的作答不计入掌握度")
    void nonIndependentAttemptsExcluded() {
        assertThat(MasteryScore.compute(List.of(
                attempt("q1", true, false)))).isZero();
        assertThat(MasteryScore.compute(List.of(
                attempt("q1", true, false),
                attempt("q2", true, true),
                attempt("q3", true, true),
                attempt("q4", true, true)))).isEqualTo(1.0);
    }

    @Test
    void allVoidedIsZero() {
        QuizAttempt voided = attempt("q1", true, true);
        voided.setVoided(true);

        assertThat(MasteryScore.compute(List.of(voided))).isZero();
    }

    @Test
    @DisplayName("早期错误可被后续正确逐步抵消")
    void recoveryRaisesScore() {
        double score = MasteryScore.compute(List.of(
                attempt("q1", false, true),
                attempt("q2", false, true),
                attempt("q3", true, true),
                attempt("q4", true, true),
                attempt("q5", true, true)));

        assertThat(score).isCloseTo(2.8 / 4.0, offset(1e-9));
        assertThat(score).isLessThan(0.9);
    }
}
