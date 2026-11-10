package com.masterypath.service;

import com.masterypath.domain.model.InteractionStatus;
import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.MasteryInteraction;
import com.masterypath.domain.model.PendingOption;
import com.masterypath.domain.model.PendingQuestion;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 出题注册（{@code TC-K1} / {@code TC-K2} + 连续追问预算）。
 */
class QuizRegistrationTest {

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

    private static PendingQuestion shortQuestion(String id) {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId(id);
        pending.setKnowledgePointId("k1");
        pending.setModuleId("m1");
        pending.setPrompt("2+2?");
        pending.setQuestionType(PendingQuestion.QuestionType.SHORT);
        pending.setExpectedAnswer("4");
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

    @Test
    @DisplayName("首次注册返回交互记录，状态为 awaiting_input")
    void registersFirstQuestion() {
        MasteryInteraction interaction = service.registerQuestion("p", shortQuestion("q1"), "", "");

        assertThat(interaction).isNotNull();
        assertThat(interaction.getStatus()).isEqualTo(InteractionStatus.AWAITING_INPUT);
        assertThat(interaction.getQuestion().getPrompt()).isEqualTo("2+2?");
    }

    @Test
    @DisplayName("注册后聚合持有 pendingQuestion 与计数 1")
    void registerUpdatesAggregate() {
        service.registerQuestion("p", shortQuestion("q1"), "", "");

        LearningProgress loaded = store.load("p");
        assertThat(loaded.getPendingQuestion()).isNotNull();
        assertThat(loaded.getPendingQuestion().getQuestionId()).isEqualTo("q1");
        assertThat(loaded.getConsecutiveQuestions()).isEqualTo(1);
    }

    @Test
    @DisplayName("注册发 question.registered 事件")
    void registerEmitsEvent() {
        service.registerQuestion("p", shortQuestion("q1"), "s1", "t1");

        assertThat(store.eventsSince("p", 0))
                .extracting(event -> event.get("event_type"))
                .contains("question.registered");
    }

    @Test
    @DisplayName("TC-K2：已有未终态题目时返回 null，不产生新题")
    void secondRegistrationReturnsNull() {
        service.registerQuestion("p", shortQuestion("q1"), "", "");

        MasteryInteraction second = service.registerQuestion("p", shortQuestion("q2"), "", "");

        assertThat(second).isNull();
        assertThat(store.getInteraction("p", "q2")).isNull();
    }

    @Test
    @DisplayName("TC-K2：被拒绝时保留原题不变")
    void rejectedRegistrationKeepsOriginal() {
        service.registerQuestion("p", shortQuestion("q1"), "", "");
        service.registerQuestion("p", choiceQuestion("q2"), "", "");

        LearningProgress loaded = store.load("p");
        assertThat(loaded.getPendingQuestion().getQuestionId()).isEqualTo("q1");
        assertThat(loaded.getPendingQuestion().getQuestionType())
                .isEqualTo(PendingQuestion.QuestionType.SHORT);
    }

    @Test
    @DisplayName("TC-K2：被拒绝时不增加连续追问计数")
    void rejectedRegistrationDoesNotBumpCounter() {
        service.registerQuestion("p", shortQuestion("q1"), "", "");
        service.registerQuestion("p", shortQuestion("q2"), "", "");

        assertThat(store.load("p").getConsecutiveQuestions()).isEqualTo(1);
    }

    @Test
    @DisplayName("已判分后可以注册新题（槽位释放）")
    void canRegisterAfterGraded() {
        service.registerQuestion("p", shortQuestion("q1"), "", "");
        service.grade("p", "q1", "4", 0, false, "", "");

        MasteryInteraction second = service.registerQuestion("p", shortQuestion("q2"), "", "");

        assertThat(second).isNotNull();
        assertThat(second.getInteractionId()).isEqualTo("q2");
    }

    @Test
    @DisplayName("跳过题目后可以注册新题")
    void canRegisterAfterSkip() {
        service.registerQuestion("p", shortQuestion("q1"), "", "");
        service.skipQuestion("p", "q1", "", "");

        MasteryInteraction second = service.registerQuestion("p", shortQuestion("q2"), "", "");

        assertThat(second).isNotNull();
    }

    @Test
    @DisplayName("连续追问达上限时拒绝，文案逐字")
    void consecutiveQuestionsBudget() {
        // 直接把连续追问计数预置到上限（跳过会清零计数，因此不模拟真实交互序列）
        store.mutate("p", tx -> {
            tx.progress().setConsecutiveQuestions(3);
            return tx.progress();
        });

        assertThatThrownBy(() -> service.registerQuestion("p", shortQuestion("q4"), "", ""))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessageContaining("Too many questions in a row");
    }

    @Test
    @DisplayName("连续追问未达上限时允许注册")
    void consecutiveQuestionsBelowBudget() {
        store.mutate("p", tx -> {
            tx.progress().setConsecutiveQuestions(2);
            return tx.progress();
        });

        assertThat(service.registerQuestion("p", shortQuestion("q1"), "", "")).isNotNull();
    }

    @Test
    @DisplayName("未知路径注册会创建空聚合（createIfMissing）")
    void registerOnMissingPathCreates() {
        MasteryInteraction interaction = service.registerQuestion("fresh", shortQuestion("q1"), "", "");

        assertThat(interaction).isNotNull();
        assertThat(store.load("fresh")).isNotNull();
    }

    @Test
    @DisplayName("题目携带 session 与 turn 信息")
    void registerKeepsSessionAndTurn() {
        service.registerQuestion("p", shortQuestion("q1"), "s1", "t1");

        MasteryInteraction loaded = store.getInteraction("p", "q1");
        assertThat(loaded.getSessionId()).isEqualTo("s1");
        assertThat(loaded.getTurnId()).isEqualTo("t1");
    }
}
