package com.masterypath.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.masterypath.QuizFixtures;
import com.masterypath.domain.model.QuizItem;
import com.masterypath.domain.model.QuizSet;
import com.masterypath.service.LearningService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

@Tag("storage")
class QuizSetStoreTest {

    @TempDir
    Path tempDir;

    private LearningStore store;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
        new LearningService(store).createPath("p", "learn caching", "Caching");
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    @DisplayName("题卷与题目可保存、读取、覆盖")
    void savesAndReadsQuizSet() {
        QuizSet set = QuizFixtures.set("s1", "p", 2);
        List<QuizItem> items = List.of(
                QuizFixtures.item("s1", 1, QuizFixtures.choice("q1", "A")),
                QuizFixtures.item("s1", 2, QuizFixtures.shortAnswer("q2", "4", "4")));

        store.saveQuizSet(set, items);

        assertThat(store.getQuizSet("s1").getQuestionCount()).isEqualTo(2);
        assertThat(store.listQuizSets("p")).hasSize(1);
        assertThat(store.listQuizItems("s1")).hasSize(2)
                .extracting(QuizItem::getSeq).containsExactly(1, 2);

        items.get(0).setResult("correct");
        items.get(0).setUserAnswer("A");
        store.saveQuizSet(set, items);
        assertThat(store.listQuizItems("s1").get(0).getResult()).isEqualTo("correct");
    }

    @Test
    @DisplayName("删除路径级联删除题卷")
    void deletePathCascadesQuizSets() {
        store.saveQuizSet(QuizFixtures.set("s1", "p", 1),
                List.of(QuizFixtures.item("s1", 1, QuizFixtures.choice("q1", "A"))));

        store.deletePath("p");

        assertThat(store.getQuizSet("s1")).isNull();
        assertThat(store.listQuizItems("s1")).isEmpty();
        assertThat(store.listQuizSets("p")).isEmpty();
    }
}
