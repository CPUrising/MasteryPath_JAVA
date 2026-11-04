package com.masterypath.service;

import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.domain.model.QuestionBankEntry;
import com.masterypath.storage.LearningStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 题库读写（判分入库、标签、筛选）。
 */
class QuestionBankTest {

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

    private void askAndGrade(String questionId, String answer) {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId(questionId);
        pending.setKnowledgePointId("k1");
        pending.setModuleId("m1");
        pending.setPrompt("2+2?");
        pending.setQuestionType(PendingQuestion.QuestionType.SHORT);
        pending.setExpectedAnswer("4");
        service.registerQuestion("p", pending, "", "");
        service.grade("p", questionId, answer, 0, false, "", "");
    }

    @Test
    @DisplayName("判分入库：题目正文、正确答案、用户作答、结果都落库")
    void bankEntryCapturesGrading() {
        askAndGrade("q1", "4");

        QuestionBankEntry entry = store.listBankEntries("p").get(0);
        assertThat(entry.getQuestion()).isEqualTo("2+2?");
        assertThat(entry.getCorrectAnswer()).isEqualTo("4");
        assertThat(entry.getUserAnswer()).isEqualTo("4");
        assertThat(entry.getResult()).isEqualTo("correct");
        assertThat(entry.getKnowledgePointId()).isEqualTo("k1");
        assertThat(entry.getKnowledgePointName()).isEqualTo("fact one");
    }

    @Test
    @DisplayName("答错的题目也入库")
    void wrongAnswerAlsoStored() {
        askAndGrade("q1", "999");

        QuestionBankEntry entry = store.listBankEntries("p").get(0);
        assertThat(entry.isCorrect()).isFalse();
        assertThat(entry.getResult()).isEqualTo("incorrect");
    }

    @Test
    @DisplayName("无标签时 listQuestions 返回全部")
    void listAllWhenNoTag() {
        askAndGrade("q1", "4");

        assertThat(service.listQuestions("p", "")).hasSize(1);
        assertThat(service.listQuestions("p", null)).hasSize(1);
    }

    @Test
    @DisplayName("按标签筛选")
    void filterByTag() {
        askAndGrade("q1", "4");
        String entryId = store.listBankEntries("p").get(0).getEntryId();
        service.tagQuestion(entryId, List.of("arithmetic"));

        assertThat(service.listQuestions("p", "arithmetic")).hasSize(1);
        assertThat(service.listQuestions("p", "algebra")).isEmpty();
    }

    @Test
    @DisplayName("打标签去重且保持顺序")
    void taggingDeduplicates() {
        askAndGrade("q1", "4");
        String entryId = store.listBankEntries("p").get(0).getEntryId();

        service.tagQuestion(entryId, List.of("a", "b"));
        QuestionBankEntry updated = service.tagQuestion(entryId, List.of("b", "c"));

        assertThat(updated.getTags()).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("空标签与空白标签被忽略")
    void blankTagsIgnored() {
        askAndGrade("q1", "4");
        String entryId = store.listBankEntries("p").get(0).getEntryId();

        QuestionBankEntry updated = service.tagQuestion(entryId, List.of("", "  ", "real"));

        assertThat(updated.getTags()).containsExactly("real");
    }

    @Test
    @DisplayName("对不存在的条目打标签返回 null")
    void tagMissingEntryReturnsNull() {
        assertThat(service.tagQuestion("nope", List.of("x"))).isNull();
    }

    @Test
    @DisplayName("题库按创建时间倒序返回")
    void bankOrderedByCreationDesc() {
        askAndGrade("q1", "4");
        askAndGrade("q2", "4");

        List<QuestionBankEntry> entries = store.listBankEntries("p");
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getQuestionId()).isEqualTo("q2");
    }

    @Test
    @DisplayName("空路径题库为空")
    void emptyBank() {
        assertThat(service.listQuestions("p", "")).isEmpty();
    }
}
