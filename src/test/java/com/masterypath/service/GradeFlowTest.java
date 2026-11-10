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

    // -- 无法解析的作答 -------------------------------------------------------

    @Test
    @DisplayName("选择题无法解析为标签时判错（不再有 ungraded）")
    void unparseableChoiceIsIncorrect() {
        service.registerQuestion("p", choiceQuestion("q1"), "", "");

        Map<String, Object> result = service.grade("p", "q1", "zzz", 0, false, "", "");

        assertThat(result).containsEntry("status", "graded");
        assertThat(result).containsEntry("result", "incorrect");
        LearningProgress loaded = store.load("p");
        assertThat(loaded.getLearningEvidence()).hasSize(1);
        assertThat(loaded.getQuizAttempts()).hasSize(1);
        assertThat(loaded.getPendingQuestion()).isNull();
    }

    @Test
    @DisplayName("作废后重复判分返回首次结果")
    void repeatedGradeAfterIncorrectIsAlreadyGraded() {
        service.registerQuestion("p", choiceQuestion("q1"), "", "");
        service.grade("p", "q1", "zzz", 0, false, "", "");

        Map<String, Object> second = service.grade("p", "q1", "B", 0, false, "", "");

        assertThat(second).containsEntry("status", "already_graded");
        assertThat(second).containsEntry("result", "incorrect");
    }

    // -- 提示不改变判定 -------------------------------------------------------

    @Test
    @DisplayName("用了提示仍判对，但 independent=false 且掌握度为 0")
    void hintsDoNotDowngradeButAreNotIndependent() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");

        Map<String, Object> result = service.grade("p", "q1", "4", 1, false, "", "");

        assertThat(result).containsEntry("result", "correct");
        assertThat(result).containsEntry("gate_cleared", false);
        LearningProgress loaded = store.load("p");
        assertThat(loaded.getMasteryLevels().getOrDefault("k1", 0.0)).isEqualTo(0.0);
        assertThat(loaded.getQuizAttempts().get(0).isIndependent()).isFalse();
    }

    @Test
    @DisplayName("无提示的正确作答 independent=true")
    void noHintsStaysCorrect() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");

        Map<String, Object> result = service.grade("p", "q1", "4", 0, false, "", "");

        assertThat(result).containsEntry("result", "correct");
        assertThat(store.load("p").getQuizAttempts().get(0).isIndependent()).isTrue();
    }

    // -- 开放题 ---------------------------------------------------------------

    @Test
    @DisplayName("开放题按期望答案关键词判对，但一次正确仍受置信上限")
    void openCorrectIsCapped() {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q1");
        pending.setKnowledgePointId("k1");
        pending.setModuleId("m1");
        pending.setPrompt("explain");
        pending.setQuestionType(PendingQuestion.QuestionType.OPEN);
        pending.setExpectedAnswer("photosynthesis,chlorophyll");
        service.registerQuestion("p", pending, "", "");

        Map<String, Object> result = service.grade(
                "p", "q1", "photosynthesis and chlorophyll", 0, false, "", "");

        assertThat(result).containsEntry("result", "correct");
        assertThat(result).containsEntry("gate_cleared", false);
        assertThat(result.get("mastery_after")).isEqualTo(0.5);
    }

    // -- 掌握推进 -------------------------------------------------------------

    /** 连续答对 {@code times} 次（每次重新登记一道题）。 */
    private void answerCorrectly(int times) {
        for (int index = 0; index < times; index++) {
            service.registerQuestion("p", shortQuestion("q" + index, "4"), "", "");
            service.grade("p", "q" + index, "4", 0, false, "", "");
        }
    }

    @Test
    @DisplayName("一次答对受置信上限限制，不能清除门槛")
    void singleCorrectDoesNotClearGate() {
        service.registerQuestion("p", shortQuestion("q1", "4"), "", "");

        Map<String, Object> result = service.grade("p", "q1", "4", 0, false, "", "");

        assertThat(result).containsEntry("result", "correct");
        assertThat(result).containsEntry("gate_cleared", false);
        assertThat(result.get("mastery_after")).isEqualTo(0.5);
    }

    @Test
    @DisplayName("三次答对后门槛清除，来源为 system")
    void correctAnswerClearsGate() {
        answerCorrectly(3);

        LearningProgress loaded = store.load("p");
        assertThat(loaded.getMasteryLevels().getOrDefault("k1", 0.0)).isEqualTo(1.0);
        assertThat(MasteryPolicy.isMastered(loaded,
                loaded.findKnowledgePoint("k1").knowledgePoint())).isTrue();
        assertThat(MasteryPolicy.masterySource(loaded,
                loaded.findKnowledgePoint("k1").knowledgePoint())).isEqualTo("system");
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
    @DisplayName("三次答对后 nextObjective 越过该目标")
    void correctAnswerAdvancesFrontier() {
        answerCorrectly(3);

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
        service.skipQuestion("p", "q1", "", "");

        assertThatThrownBy(() -> service.grade("p", "q1", "4", 0, false, "", ""))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("interaction is not gradable");
    }

    @Test
    @DisplayName("判分发 attempt.graded 与 objective.mastered 事件")
    void gradeEmitsEvents() {
        answerCorrectly(3);

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
