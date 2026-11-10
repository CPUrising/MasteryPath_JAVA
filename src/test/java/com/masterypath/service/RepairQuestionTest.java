package com.masterypath.service;

import com.masterypath.domain.model.AssessmentResult;
import com.masterypath.domain.model.AssessmentType;
import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningEvidence;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.MasteryInteraction;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.domain.model.QuestionBankEntry;
import com.masterypath.storage.LearningStore;
import com.masterypath.storage.PreconditionFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 题目修复（{@code ADR-020}，{@code TC-R1}…{@code TC-R3}）。
 *
 * <p>对照标准：{@code ../MasteryPath/tests/test_repair_profile.py} 的前 3 个用例。
 * 核心原则：<b>历史永不删除，只标记</b>。
 */
class RepairQuestionTest {

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

    // -- TC-R1 void -----------------------------------------------------------

    @Test
    @DisplayName("TC-R1：void 清除掌握度与复习计划，并标记题库")
    void voidRemovesMasteryAndMarksBank() {
        // 三次正确才过门槛；作废其中一次后剩余两次受置信上限限制（0.8），门槛重新关闭。
        register("q0", "4");
        service.grade("p", "q0", "4", 0, false, "", "");
        Map<String, Object> graded = quizAndGrade("4");
        register("q2", "4");
        service.grade("p", "q2", "4", 0, false, "", "");
        assertThat(graded).containsEntry("gate_cleared", false);
        assertThat(store.load("p").getMasteryLevels().get("k1")).isGreaterThanOrEqualTo(0.9);
        assertThat(store.load("p").getRepetitionStates()).containsKey("k1");

        Map<String, Object> result = service.repairQuestion(
                "p", "q1", "void", "", "the prompt was malformed", "", "");

        assertThat(result).containsEntry("action", "void").containsEntry("status", "repaired");
        LearningProgress progress = store.load("p");
        assertThat(progress.getMasteryLevels().getOrDefault("k1", 0.0)).isEqualTo(0.8);
        assertThat(progress.getQuizAttempts().get(1).isVoided()).isTrue();
        assertThat(progress.getLearningEvidence().get(1).isVoided()).isTrue();
        assertThat(progress.getRepetitionStates()).doesNotContainKey("k1");
        assertThat(progress.getReviewQueue()).isEmpty();
        assertThat(store.listBankEntries("p")).anyMatch(entry -> "voided".equals(entry.getResult()));
        assertThat(eventTypes()).contains("assessment.voided");
    }

    @Test
    @DisplayName("TC-R1：void 把 interaction 置为 ABANDONED")
    void voidAbandonsInteraction() {
        quizAndGrade("4");

        service.repairQuestion("p", "q1", "void", "", "bad card", "", "");

        assertThat(store.getInteraction("p", "q1").getStatus().value()).isEqualTo("abandoned");
    }

    @Test
    @DisplayName("void 只清空同一道题的待答题，不误清别的目标")
    void voidKeepsPendingQuestionOfOtherQuestion() {
        quizAndGrade("4");
        register("q2", "4");

        service.repairQuestion("p", "q1", "void", "", "bad card", "", "");

        PendingQuestion pending = store.load("p").getPendingQuestion();
        assertThat(pending).isNotNull();
        assertThat(pending.getQuestionId()).isEqualTo("q2");
    }

    @Test
    @DisplayName("void 的 void_reason 记录审计原因")
    void voidRecordsReason() {
        quizAndGrade("4");

        service.repairQuestion("p", "q1", "void", "", "the prompt was malformed", "", "");

        LearningProgress progress = store.load("p");
        assertThat(progress.getQuizAttempts().get(0).getVoidReason())
                .isEqualTo("the prompt was malformed");
        assertThat(progress.getLearningEvidence().get(0).getVoidReason())
                .isEqualTo("the prompt was malformed");
    }

    // -- TC-R2 correct --------------------------------------------------------

    @Test
    @DisplayName("TC-R2：correct 重判已存作答并恢复门槛")
    void correctRegradesStoredAnswerAndRestoresGate() {
        // 前两次独立正确（0.8，未过门槛）；q1 的答案键写错了：学习者答 "9" 才是对的。
        register("q0", "4");
        service.grade("p", "q0", "4", 0, false, "", "");
        register("m1", "4");
        service.grade("p", "m1", "4", 0, false, "", "");
        quizAndGrade("9");
        assertThat(store.load("p").getMasteryLevels().getOrDefault("k1", 0.0)).isLessThan(0.9);
        assertThat(store.load("p").getRepetitionStates()).isEmpty();

        Map<String, Object> result = service.repairQuestion(
                "p", "q1", "correct", "9", "the answer key said 4", "", "");

        assertThat(result).containsEntry("is_correct", true).containsEntry("action", "correct");
        LearningProgress progress = store.load("p");
        assertThat(progress.getQuizAttempts().get(2).isCorrect()).isTrue();
        assertThat(progress.getLearningEvidence()
                .get(progress.getLearningEvidence().size() - 1).getResult())
                .isEqualTo(AssessmentResult.CORRECT);
        assertThat(progress.getMasteryLevels().get("k1")).isGreaterThanOrEqualTo(0.9);
        assertThat(progress.getRepetitionStates()).containsKey("k1");
        assertThat(store.listBankEntries("p")).anyMatch(entry -> entry.isCorrect());
        assertThat(eventTypes()).contains("assessment.corrected", "review.scheduled");
    }

    @Test
    @DisplayName("TC-R2：correct 后 interaction 结果含 repaired=true")
    void correctMarksInteractionRepaired() {
        quizAndGrade("9");

        service.repairQuestion("p", "q1", "correct", "9", "wrong key", "", "");

        MasteryInteraction interaction = store.getInteraction("p", "q1");
        assertThat(interaction.getStatus().value()).isEqualTo("graded");
        assertThat(interaction.getResult()).containsEntry("repaired", true);
    }

    @Test
    @DisplayName("correct 追加新证据，旧证据标记为「答案键已改」")
    void correctAppendsEvidenceAndVoidsPrior() {
        quizAndGrade("9");
        int before = store.load("p").getLearningEvidence().size();

        service.repairQuestion("p", "q1", "correct", "9", "wrong key", "", "");

        List<LearningEvidence> evidence = store.load("p").getLearningEvidence();
        assertThat(evidence).hasSize(before + 1);
        assertThat(evidence.get(0).isVoided()).isTrue();
        assertThat(evidence.get(0).getVoidReason()).isEqualTo("answer key corrected: wrong key");
        assertThat(evidence.get(before).isVoided()).isFalse();
    }

    @Test
    @DisplayName("correct 复活 attempt（voided 置回 false）")
    void correctRevivesAttempt() {
        quizAndGrade("9");
        service.repairQuestion("p", "q1", "void", "", "bad", "", "");
        assertThat(store.load("p").getQuizAttempts().get(0).isVoided()).isTrue();

        service.repairQuestion("p", "q1", "correct", "9", "wrong key", "", "");

        assertThat(store.load("p").getQuizAttempts().get(0).isVoided()).isFalse();
        assertThat(store.load("p").getQuizAttempts().get(0).isCorrect()).isTrue();
    }

    @Test
    @DisplayName("correct 不覆盖已作废证据的原因（审计链不断）")
    void correctKeepsReasonOfAlreadyVoidedEvidence() {
        quizAndGrade("9");
        service.repairQuestion("p", "q1", "void", "", "first reason", "", "");

        service.repairQuestion("p", "q1", "correct", "9", "second reason", "", "");

        assertThat(store.load("p").getLearningEvidence().get(0).getVoidReason())
                .isEqualTo("first reason");
    }

    // -- 参数校验 -------------------------------------------------------------

    @Test
    @DisplayName("非法 action 被拒绝，文案逐字")
    void invalidActionRejected() {
        quizAndGrade("4");

        assertThatThrownBy(() -> service.repairQuestion("p", "q1", "explode", "", "x", "", ""))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("action must be 'void' or 'correct'");
    }

    @Test
    @DisplayName("缺 reason 被拒绝，文案逐字")
    void missingReasonRejected() {
        quizAndGrade("4");

        assertThatThrownBy(() -> service.repairQuestion("p", "q1", "void", "", "  ", "", ""))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("a reason is required so the repair can be audited");
    }

    @Test
    @DisplayName("correct 缺 expected_answer 被拒绝，文案逐字")
    void correctWithoutExpectedAnswerRejected() {
        quizAndGrade("4");

        assertThatThrownBy(() -> service.repairQuestion("p", "q1", "correct", "", "x", "", ""))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("correcting a question requires the new expected_answer");
    }

    @Test
    @DisplayName("无任何可修复题目时拒绝，文案逐字")
    void noQuestionAvailableRejected() {
        assertThatThrownBy(() -> service.repairQuestion("p", "", "void", "", "x", "", ""))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("no question is available to repair");
    }

    @Test
    @DisplayName("题号省略时回退到最近一次未作废的 attempt")
    void resolvesQuestionIdFromAttempts() {
        quizAndGrade("4");

        Map<String, Object> result = service.repairQuestion("p", "", "void", "", "bad", "", "");

        assertThat(result).containsEntry("question_id", "q1");
    }

    @Test
    @DisplayName("correct 后仍判错时正常返回 false")
    void correctionCanStillBeIncorrect() {
        // 作答 "A" 可解析但选错；把答案键改成 "B" 后重判仍是 incorrect。
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q1");
        pending.setKnowledgePointId("k1");
        pending.setModuleId("m1");
        pending.setPrompt("2+2?");
        pending.setQuestionType(PendingQuestion.QuestionType.CHOICE);
        pending.setExpectedAnswer("B");
        pending.setOptions(List.of(
                new com.masterypath.domain.model.PendingOption("A", "3"),
                new com.masterypath.domain.model.PendingOption("B", "4")));
        service.registerQuestion("p", pending, "", "");
        service.grade("p", "q1", "A", 0, false, "", "");

        Map<String, Object> result = service.repairQuestion(
                "p", "q1", "correct", "B", "x", "", "");

        assertThat(result).containsEntry("action", "correct")
                .containsEntry("is_correct", false)
                .containsEntry("result", "incorrect");
    }

    @Test
    @DisplayName("新答案键仍无法判定时拒绝修复，且不写 assessment.corrected")
    void correctionRefusedWhenStillUngraded() {
        // 作答 "zzz" 无法解析为标签，且新键仍是标签 → 重判 ungraded。
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q1");
        pending.setKnowledgePointId("k1");
        pending.setModuleId("m1");
        pending.setPrompt("2+2?");
        pending.setQuestionType(PendingQuestion.QuestionType.CHOICE);
        pending.setExpectedAnswer("A");
        pending.setOptions(List.of(
                new com.masterypath.domain.model.PendingOption("A", "3"),
                new com.masterypath.domain.model.PendingOption("B", "4")));
        service.registerQuestion("p", pending, "", "");
        service.grade("p", "q1", "zzz", 0, false, "", "");

        assertThatThrownBy(() -> service.repairQuestion("p", "q1", "correct", "B", "x", "", ""))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessageContaining("corrected answer cannot be graded");
        assertThat(eventTypes()).doesNotContain("assessment.corrected");
    }

    // -- 事件与返回值 ---------------------------------------------------------

    @Test
    @DisplayName("事件 payload 含 7 个键")
    void eventPayloadHasSevenKeys() {
        quizAndGrade("9");

        service.repairQuestion("p", "q1", "correct", "9", "wrong key", "s1", "t1");

        String payload = lastPayloadOf("assessment.corrected");
        assertThat(payload).contains("\"question_id\":\"q1\"")
                .contains("\"knowledge_point_id\":\"k1\"")
                .contains("\"reason\":\"wrong key\"")
                .contains("\"previous_expected\":\"4\"")
                .contains("\"expected_answer\":\"9\"")
                .contains("\"previous_is_correct\":false")
                .contains("\"is_correct\":true");
    }

    @Test
    @DisplayName("返回值含 9 个键，path_revision 为 version+1")
    void resultHasNineKeys() {
        quizAndGrade("9");
        int versionBefore = store.load("p").getVersion();

        Map<String, Object> result = service.repairQuestion(
                "p", "q1", "correct", "9", "wrong key", "", "");

        assertThat(result).containsOnlyKeys("status", "action", "question_id",
                "knowledge_point_id", "path_revision", "is_correct", "result",
                "mastery", "mastered", "next");
        assertThat(result).containsEntry("path_revision", versionBefore + 1)
                .containsEntry("result", "correct");
    }

    @Test
    @DisplayName("对定性目标修复不伪造分数（INV-05）")
    void repairDoesNotFabricateScoreForQualitative() {
        store.create(ServiceFixtures.onePoint("c", KnowledgeType.CONCEPT));
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("qc");
        pending.setKnowledgePointId("k1");
        pending.setModuleId("m1");
        pending.setPrompt("explain?");
        pending.setQuestionType(PendingQuestion.QuestionType.SHORT);
        pending.setExpectedAnswer("4");
        service.registerQuestion("c", pending, "", "");
        service.grade("c", "qc", "9", 0, false, "", "");

        service.repairQuestion("c", "qc", "correct", "9", "wrong key", "", "");

        // 定性类型的掌握由 qualitativeMastery 决定，修复不写分数。
        assertThat(store.load("c").getMasteryLevels()).isEmpty();
    }

    // -- 辅助 ----------------------------------------------------------------

    private Map<String, Object> quizAndGrade(String answer) {
        register("q1", "4");
        return service.grade("p", "q1", answer, 0, false, "", "");
    }

    private void register(String questionId, String expected) {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId(questionId);
        pending.setKnowledgePointId("k1");
        pending.setModuleId("m1");
        pending.setPrompt("2+2?");
        pending.setQuestionType(PendingQuestion.QuestionType.SHORT);
        pending.setExpectedAnswer(expected);
        service.registerQuestion("p", pending, "", "");
    }

    private List<String> eventTypes() {
        return store.eventsSince("p", 0).stream()
                .map(event -> String.valueOf(event.get("event_type")))
                .toList();
    }

    private String lastPayloadOf(String eventType) {
        List<String> payloads = store.eventsSince("p", 0).stream()
                .filter(event -> eventType.equals(event.get("event_type")))
                .map(event -> String.valueOf(event.get("payload")))
                .toList();
        return payloads.get(payloads.size() - 1);
    }
}
