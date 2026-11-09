package com.masterypath.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.masterypath.QuizFixtures;
import com.masterypath.domain.model.QuizItem;
import com.masterypath.domain.model.QuizSet;
import com.masterypath.storage.LearningStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class QuizSetServiceTest {

    @TempDir
    Path tempDir;

    private LearningStore store;
    private LearningService learning;
    private QuizSetService service;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
        learning = new LearningService(store);
        service = new QuizSetService(store);
        learning.createPath("p", "learn caching", "Caching");
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    @DisplayName("提交后逐题判分并计算得分；掌握闭环不变")
    void submitGradesAndLeavesMasteryUntouched() {
        List<QuizItem> items = twoItems("s1");
        service.save(QuizFixtures.set("s1", "p", 2), items);

        var before = store.load("p");
        var outcome = service.submit("p", "s1", Map.of("q1", "A", "q2", "5"));

        assertThat(outcome.status()).isEqualTo("submitted");
        assertThat(outcome.set().getScore()).isEqualTo(0.5);
        assertThat(outcome.items().get(0).getResult()).isEqualTo("correct");
        assertThat(outcome.items().get(1).getResult()).isEqualTo("incorrect");

        var after = store.load("p");
        assertThat(after.getMasteryLevels()).isEqualTo(before.getMasteryLevels());
        assertThat(after.getLearningEvidence()).isEmpty();
        assertThat(after.getPendingQuestion()).isNull();
    }

    @Test
    @DisplayName("含 ungraded 的提交零状态变更")
    void ungradedLeavesNoTrace() {
        service.save(QuizFixtures.set("s1", "p", 1),
                List.of(QuizFixtures.item("s1", 1, QuizFixtures.choice("q1", "A"))));

        var outcome = service.submit("p", "s1", Map.of("q1", "Z"));

        assertThat(outcome.status()).isEqualTo("ungraded");
        assertThat(outcome.ungradedReasons()).isNotEmpty();
        assertThat(store.getQuizSet("s1").getStatus()).isEqualTo(QuizSet.STATUS_OPEN);
        assertThat(store.listQuizItems("s1").get(0).getResult()).isEmpty();
    }

    @Test
    @DisplayName("重复提交返回首次结果（幂等）")
    void repeatedSubmitIsIdempotent() {
        service.save(QuizFixtures.set("s1", "p", 1),
                List.of(QuizFixtures.item("s1", 1, QuizFixtures.choice("q1", "A"))));

        service.submit("p", "s1", Map.of("q1", "A"));
        var replay = service.submit("p", "s1", Map.of("q1", "B"));

        assertThat(replay.status()).isEqualTo("submitted");
        assertThat(replay.items().get(0).getResult()).isEqualTo("correct");
        assertThat(replay.items().get(0).getUserAnswer()).isEqualTo("A");
    }

    @Test
    @DisplayName("提交前隐藏答案，提交后释放")
    void viewHidesThenReleasesAnswers() {
        service.save(QuizFixtures.set("s1", "p", 1),
                List.of(QuizFixtures.item("s1", 1, QuizFixtures.choice("q1", "A"))));

        Map<String, Object> before = service.view("p", "s1", false);
        assertThat(question(before)).doesNotContainKey("correct_answer");

        service.submit("p", "s1", Map.of("q1", "A"));
        Map<String, Object> after = service.view("p", "s1", true);
        assertThat(question(after)).containsEntry("correct_answer", "A");
    }

    private static List<QuizItem> twoItems(String setId) {
        return List.of(
                QuizFixtures.item(setId, 1, QuizFixtures.choice("q1", "A")),
                QuizFixtures.item(setId, 2, QuizFixtures.shortAnswer("q2", "4", "4")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> question(Map<String, Object> view) {
        return ((List<Map<String, Object>>) view.get("questions")).get(0);
    }
}
