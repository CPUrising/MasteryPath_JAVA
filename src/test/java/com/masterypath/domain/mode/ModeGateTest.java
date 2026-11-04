package com.masterypath.domain.mode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 门控判定、拒绝文案与准入（{@code TC-D1}…{@code TC-D8}）。
 */
class ModeGateTest {

    // -- TC-D1 / TC-D2：模式专属工具的拒绝 ------------------------------------

    @Test
    @DisplayName("TC-D1：outline 下拒绝 mastery_quiz")
    void quizRefusedInOutline() {
        assertThat(ModeGate.toolIsAllowed("mastery_quiz", SessionMode.OUTLINE)).isFalse();
    }

    @Test
    @DisplayName("TC-D2：study 下拒绝 mastery_build")
    void buildRefusedInStudy() {
        assertThat(ModeGate.toolIsAllowed("mastery_build", SessionMode.STUDY)).isFalse();
    }

    @Test
    @DisplayName("TC-D2：review 下也拒绝 mastery_build")
    void buildRefusedInReview() {
        assertThat(ModeGate.toolIsAllowed("mastery_build", SessionMode.REVIEW)).isFalse();
    }

    @Test
    @DisplayName("outline 下允许 mastery_build")
    void buildAllowedInOutline() {
        assertThat(ModeGate.toolIsAllowed("mastery_build", SessionMode.OUTLINE)).isTrue();
    }

    // -- TC-D3：共享工具 ------------------------------------------------------

    @Test
    @DisplayName("TC-D3：共享工具在任意模式放行")
    void sharedToolAllowedEverywhere() {
        for (String tool : ToolClassification.SHARED_TOOLS) {
            for (SessionMode mode : SessionMode.values()) {
                assertThat(ModeGate.toolIsAllowed(tool, mode.value()))
                        .as("%s in %s", tool, mode)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("TC-D3：未登记的工具视为共享，放行")
    void unlistedToolIsShared() {
        assertThat(ModeGate.toolIsAllowed("some_future_tool", SessionMode.OUTLINE)).isTrue();
    }

    // -- TC-D4：模式缺失不门控 ------------------------------------------------

    @Test
    @DisplayName("TC-D4：null / 空串 / 未知模式一律放行全部工具")
    void unrecordedModeEnforcesNothing() {
        assertThat(ModeGate.toolIsAllowed("mastery_build", null)).isTrue();
        assertThat(ModeGate.toolIsAllowed("mastery_quiz", "")).isTrue();
        assertThat(ModeGate.toolIsAllowed("mastery_quiz", "bogus")).isTrue();
        assertThat(ModeGate.toolIsAllowed("mastery_revise", "   ")).isTrue();
    }

    // -- TC-D5 / TC-D6：准入 --------------------------------------------------

    @Test
    @DisplayName("TC-D5：无大纲进 study 被拒，文案提示留在 outline")
    void studyWithoutOutlineRefused() {
        String error = ModeGate.admissionError(SessionMode.STUDY, false);

        assertThat(error).isNotEmpty();
        assertThat(error).contains("no outline").contains("'outline'");
    }

    @Test
    @DisplayName("TC-D6：有大纲进 study 放行")
    void studyWithOutlineAllowed() {
        assertThat(ModeGate.admissionError(SessionMode.STUDY, true)).isEmpty();
    }

    @Test
    @DisplayName("outline 与 review 无准入条件")
    void otherModesHaveNoAdmissionGate() {
        assertThat(ModeGate.admissionError(SessionMode.OUTLINE, false)).isEmpty();
        assertThat(ModeGate.admissionError(SessionMode.REVIEW, false)).isEmpty();
    }

    @Test
    @DisplayName("准入用 normalize：未知模式按 study 判，无大纲即被拒")
    void admissionTreatsUnknownModeAsStudy() {
        assertThat(ModeGate.admissionError("bogus", false)).isNotEmpty();
        assertThat(ModeGate.admissionError(null, false)).isNotEmpty();
        assertThat(ModeGate.admissionError("bogus", true)).isEmpty();
    }

    // -- TC-D7 / TC-D8：归属与 review 许可 ------------------------------------

    @Test
    @DisplayName("TC-D7：mastery_quiz 归属 study 与 review，顺序稳定")
    void quizOwnedByStudyThenReview() {
        assertThat(ModeGate.owningModes("mastery_quiz"))
                .containsExactly(SessionMode.STUDY, SessionMode.REVIEW);
        assertThat(ModeGate.toolIsAllowed("mastery_quiz", SessionMode.REVIEW)).isTrue();
        assertThat(ModeGate.toolIsAllowed("mastery_quiz", SessionMode.OUTLINE)).isFalse();
    }

    @Test
    @DisplayName("TC-D7：mastery_build 归属仅 outline")
    void buildOwnedByOutlineOnly() {
        assertThat(ModeGate.owningModes("mastery_build"))
                .containsExactly(SessionMode.OUTLINE);
    }

    @Test
    @DisplayName("TC-D7：共享工具无归属模式")
    void sharedToolHasNoOwningMode() {
        assertThat(ModeGate.owningModes("mastery_status")).isEmpty();
        assertThat(ModeGate.owningModes("rag")).isEmpty();
        assertThat(ModeGate.owningModes("unknown_tool")).isEmpty();
    }

    @Test
    @DisplayName("TC-D8：review 下放行 mastery_assess（模式许可级）")
    void reviewAllowsAssess() {
        assertThat(ModeGate.toolIsAllowed("mastery_assess", SessionMode.REVIEW)).isTrue();
    }

    @Test
    @DisplayName("TC-D8：review 下放行全部 study/review 工具")
    void reviewAllowsAllStudyReviewTools() {
        for (String tool : ToolClassification.STUDY_REVIEW) {
            assertThat(ModeGate.toolIsAllowed(tool, SessionMode.REVIEW))
                    .as(tool)
                    .isTrue();
        }
    }

    // -- 拒绝文案 -------------------------------------------------------------

    @Test
    @DisplayName("拒绝文案含 mastery_mode 与目标模式（等价于 test_wrong_mode_message_names_target）")
    void wrongModeMessageNamesTarget() {
        String message = ModeGate.wrongModeMessage("mastery_quiz", SessionMode.OUTLINE);

        assertThat(message).contains("mastery_mode");
        assertThat(message).contains("'study'");
    }

    @Test
    @DisplayName("拒绝文案逐字复刻参考实现")
    void wrongModeMessageIsVerbatim() {
        assertThat(ModeGate.wrongModeMessage("mastery_quiz", SessionMode.OUTLINE))
                .isEqualTo("mastery_quiz belongs to the 'study' or 'review' mode, "
                        + "and this conversation is in 'outline'. "
                        + "Call mastery_mode with mode='study' first — "
                        + "tell the learner what you are switching to and why.");
    }

    @Test
    @DisplayName("build 的拒绝文案只列 outline")
    void wrongModeMessageForBuild() {
        assertThat(ModeGate.wrongModeMessage("mastery_build", SessionMode.STUDY))
                .isEqualTo("mastery_build belongs to the 'outline' mode, "
                        + "and this conversation is in 'study'. "
                        + "Call mastery_mode with mode='outline' first — "
                        + "tell the learner what you are switching to and why.");
    }

    @Test
    @DisplayName("未知模式在文案里按 study 展示")
    void wrongModeMessageNormalizesUnknownMode() {
        assertThat(ModeGate.wrongModeMessage("mastery_build", "bogus"))
                .contains("this conversation is in 'study'");
    }

    @Test
    @DisplayName("无归属工具（理论上不可达）给出兜底文案")
    void wrongModeMessageFallsBackWhenNoOwner() {
        assertThat(ModeGate.wrongModeMessage("phantom_tool", SessionMode.OUTLINE))
                .isEqualTo("phantom_tool cannot run in 'outline'.");
    }
}
