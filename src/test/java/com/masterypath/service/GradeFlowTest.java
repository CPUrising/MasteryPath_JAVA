package com.masterypath.service;

import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.PendingOption;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.domain.policy.MasteryPolicy;
import com.masterypath.storage.LearningStore;
import com.masterypath.storage.PreconditionFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 判分流程（{@code TC-K3} / {@code TC-K4} / {@code TC-K5} / {@code TC-E3} + 掌握推进）。
 *
 * <p>核心：幂等、{@code ungraded} 零变更、提示降级、定性类型不写分。
 */
class GradeFlowTest {

    @TempDir
    Path tempDir;

    private LearningStore store;
    private LearningService service;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
        service = new LearningService(store);
        store.create(ServiceFixtures.onePoint("p", KnowledgeType.MEMORY));
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    private static PendingQuestion shortQuestion(String id, String expected) {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId(id);
        pending.setKnowledgePointId("k1");
        pending.setModuleId("m1");
        pending.setPrompt("2+2?");
        pending.setQuestionType(PendingQuestion.QuestionType.SHORT);
        pending.setExpectedAnswer(expected);
        return pending;
    }

    private static PendingQuestion choiceQuestion(String id) {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId(id);
        pending.setKnowledgePointId("k1");
        pending.setModuleId("m1");
        pending.setPrompt("2+2?");
        pending.setQuestionType(PendingQuestion.QuestionType.CHOICE);
        pending.setExpectedAnswer("B");
        List<PendingOption> options = new ArrayList<>();
        options.add(new PendingOption("A", "3"));
        options.add(new PendingOption("B", "4"));
        pending.setOptions(options);
        return pending;
    }

    // -- TC-K3 / TC-E3 幂等 ---------------------------------------------------

    @Test
    @DisplayName("TC-K3：首次判分返回 graded")
    void firstGradeIsGraded() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");

        Map<String, Object> result = service.grade("p", "q1", "4", 0, false, "", "");

        assertThat(result).containsEntry("status", "graded");
        assertThat(result).containsEntry("result", "correct");
        assertThat(result).containsEntry("is_correct", true);
    }

    @Test
    @DisplayName("TC-K3/TC-E3：重复判分返回 already_graded 且证据仍为 1 条")
    void gradeIsIdempotent() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");
        Map<String, Object> first = service.grade("p", "q1", "4", 0, false, "", "");

        Map<String, Object> second = service.grade("p", "q1", "4", 0, false, "", "");

        assertThat(second).containsEntry("status", "already_graded");
        assertThat(second.get("result")).isEqualTo(first.get("result"));
        assertThat(store.load("p").getLearningEvidence()).hasSize(1);
        assertThat(store.load("p").getQuizAttempts()).hasSize(1);
    }

    @Test
    @DisplayName("TC-K3：第二次判分带不同答案也返回首次结果")
    void idempotentIgnoresNewAnswer() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");
        service.grade("p", "q1", "4", 0, false, "", "");

        Map<String, Object> second = service.grade("p", "q1", "999", 0, false, "", "");

        assertThat(second).containsEntry("result", "correct");
        assertThat(store.load("p").getLearningEvidence()).hasSize(1);
    }

    // -- TC-K4 ungraded -------------------------------------------------------

    @Test
    @DisplayName("TC-K4：无法解析的选择题返回 ungraded")
    void unparseableChoiceIsUngraded() {
        service.registerQuestion("p", choiceQuestion("q1"), "", "");

        Map<String, Object> result = service.grade("p", "q1", "zzz", 0, false, "", "");

        assertThat(result).containsEntry("status", "ungraded");
        assertThat(result.get("reason")).asString().contains("could not tell which option");
    }

    @Test
    @DisplayName("TC-K4：ungraded 时零状态变更")
    void ungradedLeavesNoTrace() {
        service.registerQuestion("p", choiceQuestion("q1"), "", "");

        service.grade("p", "q1", "zzz", 0, false, "", "");

        LearningProgress loaded = store.load("p");
        assertThat(loaded.getMasteryLevels()).isEmpty();
        assertThat(loaded.getLearningEvidence()).isEmpty();
        assertThat(loaded.getQuizAttempts()).isEmpty();
        assertThat(loaded.getPendingQuestion()).isNotNull();   // 槽位仍被占据
    }

    @Test
    @DisplayName("TC-K4：ungraded 后仍可用正确答案判分")
    void canGradeAfterUngraded() {
        service.registerQuestion("p", choiceQuestion("q1"), "", "");
        service.grade("p", "q1", "zzz", 0, false, "", "");

        Map<String, Object> result = service.grade("p", "q1", "B", 0, false, "", "");

        assertThat(result).containsEntry("status", "graded");
    }

    // -- TC-K5 提示降级 -------------------------------------------------------

    @Test
    @DisplayName("TC-K5：用了提示的正确作答降级为 partial")
    void hintsDowngradeToPartial() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");

        Map<String, Object> result = service.grade("p", "q1", "4", 1, false, "", "");

        assertThat(result).containsEntry("result", "partial");
        assertThat(result).containsEntry("gate_cleared", false);
    }

    @Test
    @DisplayName("TC-K5：有提示时不写满分，掌握不打开")
    void hintsPreventMastery() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");
        service.grade("p", "q1", "4", 1, false, "", "");

        LearningProgress loaded = store.load("p");
        assertThat(loaded.getMasteryLevels().getOrDefault("k1", 0.0)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("TC-K5：无提示的正确作答不降级")
    void noHintsStaysCorrect() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");

        Map<String, Object> result = service.grade("p", "q1", "4", 0, false, "", "");

        assertThat(result).containsEntry("result", "correct");
    }

    // -- 开放题降级 -----------------------------------------------------------

    @Test
    @DisplayName("开放题判 correct 时服务层降级为 partial（INV-35）")
    void openCorrectDowngradedToPartial() {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q1");
        pending.setKnowledgePointId("k1");
        pending.setModuleId("m1");
        pending.setPrompt("explain");
        pending.setQuestionType(PendingQuestion.QuestionType.OPEN);
        pending.setRequiredKeywords(List.of("photosynthesis"));
        service.registerQuestion("p", pending, "", "");

        Map<String, Object> result = service.grade("p", "q1", "photosynthesis", 0, false, "", "");

        assertThat(result).containsEntry("result", "partial");
        assertThat(result).containsEntry("gate_cleared", false);
    }

    // -- 掌握推进 -------------------------------------------------------------

    @Test
    @DisplayName("定量类型首次答对后门槛清除，来源为 system")
    void correctAnswerClearsGate() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");

        Map<String, Object> result = service.grade("p", "q1", "4", 0, false, "", "");

        assertThat(result).containsEntry("gate_cleared", true);
        assertThat(result).containsEntry("source", "system");
        LearningProgress loaded = store.load("p");
        assertThat(MasteryPolicy.isMastered(loaded,
                loaded.findKnowledgePoint("k1").knowledgePoint())).isTrue();
    }

    @Test
    @DisplayName("答错不打开门槛")
    void wrongAnswerDoesNotClearGate() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");

        Map<String, Object> result = service.grade("p", "q1", "999", 0, false, "", "");

        assertThat(result).containsEntry("result", "incorrect");
        assertThat(result).containsEntry("gate_cleared", false);
    }

    @Test
    @DisplayName("答对后 nextObjective 越过该目标")
    void correctAnswerAdvancesFrontier() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");
        service.grade("p", "q1", "4", 0, false, "", "");

        assertThat(MasteryPolicy.nextObjective(store.load("p")).action()).isEqualTo("complete");
    }

    @Test
    @DisplayName("判分后清空 pendingQuestion 与连续追问计数")
    void gradeClearsSlot() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");
        service.grade("p", "q1", "4", 0, false, "", "");

        LearningProgress loaded = store.load("p");
        assertThat(loaded.getPendingQuestion()).isNull();
        assertThat(loaded.getConsecutiveQuestions()).isZero();
    }

    // -- INV-05 定性类型不写分 ------------------------------------------------

    @Test
    @DisplayName("concept 判分不写分数（INV-05）")
    void conceptGradingDoesNotWriteScore() {
        store.create(ServiceFixtures.onePoint("p2", KnowledgeType.CONCEPT));
        PendingQuestion pending = shortQuestion("q1", "4");
        service.registerQuestion("p2", pending, "", "");

        Map<String, Object> result = service.grade("p2", "q1", "4", 0, false, "", "");

        LearningProgress loaded = store.load("p2");
        assertThat(loaded.getMasteryLevels()).doesNotContainKey("k1");
        assertThat(result).containsEntry("gate_cleared", false);
        assertThat(result.get("mastery_after")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("concept 判分仍追加证据与作答")
    void conceptGradingStillRecordsEvidence() {
        store.create(ServiceFixtures.onePoint("p2", KnowledgeType.CONCEPT));
        service.registerQuestion("p2", shortQuestion("q1", "4"), "", "");

        service.grade("p2", "q1", "4", 0, false, "", "");

        LearningProgress loaded = store.load("p2");
        assertThat(loaded.getLearningEvidence()).hasSize(1);
        assertThat(loaded.getQuizAttempts()).hasSize(1);
    }

    // -- 错误路径 -------------------------------------------------------------

    @Test
    @DisplayName("未知 question_id 判分被拒绝")
    void unknownQuestionRejected() {
        assertThatThrownBy(() -> service.grade("p", "nope", "4", 0, false, "", ""))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("unknown question_id: nope");
    }

    @Test
    @DisplayName("已作废的题目不可判分")
    void abandonedQuestionNotGradable() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");
        service.skipQuestion("p", "q1");

        assertThatThrownBy(() -> service.grade("p", "q1", "4", 0, false, "", ""))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("interaction is not gradable");
    }

    @Test
    @DisplayName("判分发 attempt.graded 与 objective.mastered 事件")
    void gradeEmitsEvents() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");

        service.grade("p", "q1", "4", 0, false, "", "");

        assertThat(store.eventsSince("p", 0))
                .extracting(event -> event.get("event_type"))
                .contains("attempt.graded", "objective.mastered");
    }

    @Test
    @DisplayName("答错不发 objective.mastered")
    void wrongAnswerEmitsNoMastered() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");

        service.grade("p", "q1", "999", 0, false, "", "");

        assertThat(store.eventsSince("p", 0))
                .extracting(event -> event.get("event_type"))
                .doesNotContain("objective.mastered");
    }

    @Test
    @DisplayName("review 标记会写入 REVIEW 类型证据")
    void reviewFlagRecorded() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");

        service.grade("p", "q1", "4", 0, true, "", "");

        assertThat(store.load("p").getLearningEvidence().get(0).getAssessmentType())
                .isEqualTo(com.masterypath.domain.model.AssessmentType.REVIEW);
    }

    @Test
    @DisplayName("判分写入题库条目")
    void gradeWritesBankEntry() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");

        service.grade("p", "q1", "4", 0, false, "", "");

        assertThat(store.listBankEntries("p")).hasSize(1);
        assertThat(store.listBankEntries("p").get(0).getQuestionId()).isEqualTo("q1");
        assertThat(store.listBankEntries("p").get(0).isCorrect()).isTrue();
    }

    @Test
    @DisplayName("attempt 的 independent 反映是否用过提示")
    void attemptIndependentFlag() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");
        service.grade("p", "q1", "4", 1, false, "", "");

        assertThat(store.load("p").getQuizAttempts().get(0).isIndependent()).isFalse();
    }
}
