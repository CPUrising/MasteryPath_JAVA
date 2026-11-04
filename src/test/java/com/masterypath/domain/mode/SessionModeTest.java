package com.masterypath.domain.mode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SessionMode} 的两个归一函数与提示词块。
 *
 * <p>核心：{@code enforced} 与 {@code normalize} 语义不同，不可合并。
 */
class SessionModeTest {

    @Test
    @DisplayName("TC-D4：enforced 对 null / 空串 / 未知值一律返回 null（不门控）")
    void enforcedReturnsNullForUnrecordedModes() {
        assertThat(SessionMode.enforced(null)).isNull();
        assertThat(SessionMode.enforced("")).isNull();
        assertThat(SessionMode.enforced("   ")).isNull();
        assertThat(SessionMode.enforced("bogus")).isNull();
    }

    @Test
    @DisplayName("normalize 对 null / 空串 / 未知值一律回退 study")
    void normalizeFallsBackToStudy() {
        assertThat(SessionMode.normalize(null)).isEqualTo(SessionMode.STUDY);
        assertThat(SessionMode.normalize("")).isEqualTo(SessionMode.STUDY);
        assertThat(SessionMode.normalize("bogus")).isEqualTo(SessionMode.STUDY);
    }

    @Test
    @DisplayName("两个归一函数对合法值结果一致")
    void bothResolveKnownValues() {
        for (SessionMode mode : SessionMode.values()) {
            assertThat(SessionMode.enforced(mode.value())).isEqualTo(mode);
            assertThat(SessionMode.normalize(mode.value())).isEqualTo(mode);
        }
    }

    @Test
    @DisplayName("解析容忍大小写与前后空白")
    void parsingToleratesCaseAndWhitespace() {
        assertThat(SessionMode.enforced("  STUDY ")).isEqualTo(SessionMode.STUDY);
        assertThat(SessionMode.enforced("Review")).isEqualTo(SessionMode.REVIEW);
        assertThat(SessionMode.normalize("\tOutline\n")).isEqualTo(SessionMode.OUTLINE);
    }

    @Test
    @DisplayName("DEFAULT_MODE 是 study")
    void defaultModeIsStudy() {
        assertThat(SessionMode.DEFAULT_MODE).isEqualTo(SessionMode.STUDY);
    }

    @Test
    @DisplayName("枚举声明顺序即 MODES 顺序：outline → study → review")
    void declarationOrderIsOutlineStudyReview() {
        assertThat(SessionMode.values()).containsExactly(
                SessionMode.OUTLINE, SessionMode.STUDY, SessionMode.REVIEW);
    }

    @Test
    @DisplayName("每个模式都有非空且互不相同的提示词块")
    void everyModeHasDistinctInstruction() {
        for (SessionMode mode : SessionMode.values()) {
            assertThat(mode.instruction()).isNotBlank();
        }
        assertThat(SessionMode.OUTLINE.instruction())
                .isNotEqualTo(SessionMode.STUDY.instruction());
        assertThat(SessionMode.STUDY.instruction())
                .isNotEqualTo(SessionMode.REVIEW.instruction());
    }

    @Test
    @DisplayName("提示词块逐字复刻参考实现（抽查首句与关键工具名）")
    void instructionsMatchReferenceWording() {
        assertThat(SessionMode.OUTLINE.instruction())
                .startsWith("This mode is for designing the learning outline with the learner.")
                .contains("mastery_build")
                .contains("mastery_revise");
        assertThat(SessionMode.STUDY.instruction())
                .startsWith("This mode is for teaching and working the agreed outline forward")
                .contains("mastery_build and mastery_revise belong to outline mode");
        assertThat(SessionMode.REVIEW.instruction())
                .startsWith("This mode is for re-testing objectives the learner has already mastered.")
                .contains("switch to study with mastery_mode");
    }
}
