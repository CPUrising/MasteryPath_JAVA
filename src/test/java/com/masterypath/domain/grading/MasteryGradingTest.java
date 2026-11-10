package com.masterypath.domain.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 掌握式判分（逐字复刻 DeepTutor {@code tests/test_grading.py}）。
 */
class MasteryGradingTest {

    @Test
    @DisplayName("期望答案为空时 fail-closed 判错")
    void emptyExpectedIsIncorrect() {
        assertThat(MasteryGrading.isCorrect("anything", "", "short")).isFalse();
        assertThat(MasteryGrading.isCorrect("anything", null, "choice")).isFalse();
    }

    @Test
    @DisplayName("选择题去空格、忽略大小写后整串比较")
    void choiceComparesNormalizedLabel() {
        assertThat(MasteryGrading.isCorrect(" b ", "B", "choice")).isTrue();
        assertThat(MasteryGrading.isCorrect("B", " b ", "choice")).isTrue();
        assertThat(MasteryGrading.isCorrect("A B", "AB", "choice")).isTrue();
        assertThat(MasteryGrading.isCorrect("4", "B", "choice")).isFalse();
    }

    @Test
    @DisplayName("简答题完全一致判对")
    void shortExactMatch() {
        assertThat(MasteryGrading.isCorrect("photosynthesis", "photosynthesis", "short")).isTrue();
        assertThat(MasteryGrading.isCorrect("  NEW  ", "new", "short")).isTrue();
    }

    @Test
    @DisplayName("简答题相似度 0.85 边界")
    void shortFuzzyThreshold() {
        // 14/15 个字符相同 → ratio = 2*14/29 ≈ 0.965
        assertThat(MasteryGrading.isCorrect("photosynthesi", "photosynthesis", "short")).isTrue();
        // "2" vs "2.0" → 匹配 1 个字符，ratio = 2/4 = 0.5
        assertThat(MasteryGrading.isCorrect("2", "2.0", "short")).isFalse();
        assertThat(MasteryGrading.isCorrect("2", "3", "short")).isFalse();
    }

    @Test
    @DisplayName("期望答案超过 30 字时只做精确匹配")
    void longShortOnlyExact() {
        String expected = "a".repeat(31);
        assertThat(MasteryGrading.isCorrect("a".repeat(30) + "b", expected, "short")).isFalse();
        assertThat(MasteryGrading.isCorrect(expected, expected, "short")).isTrue();
    }

    @Test
    @DisplayName("开放题按关键词命中率 0.6 判定")
    void openKeywordRatio() {
        String expected = "alpha,beta,gamma,delta,epsilon";
        assertThat(MasteryGrading.isCorrect("alpha beta gamma", expected, "open")).isTrue();
        assertThat(MasteryGrading.isCorrect("alpha beta", expected, "open")).isFalse();
    }

    @Test
    @DisplayName("开放题支持中文分隔符")
    void openSupportsCjkDelimiters() {
        String expected = "互斥；可见性。线程";
        assertThat(MasteryGrading.isCorrect("互斥 和 可见性", expected, "open")).isTrue();
        assertThat(MasteryGrading.isCorrect("只有互斥", expected, "open")).isFalse();
    }

    @Test
    @DisplayName("未知题型判错")
    void unknownTypeIsIncorrect() {
        assertThat(MasteryGrading.isCorrect("x", "x", "essay")).isFalse();
        assertThat(MasteryGrading.isCorrect("x", "x", null)).isFalse();
    }

    @Test
    @DisplayName("SequenceMatcher 使用递归匹配块，而不是编辑距离")
    void ratioUsesMatchingBlocks() {
        // 匹配块：ab(2)、d(1) → M=3，T=8 → 0.75
        assertThat(MasteryGrading.ratio("abcd", "abxd")).isCloseTo(0.75, offset(1e-9));
        assertThat(MasteryGrading.ratio("", "")).isEqualTo(1.0);
    }
}
