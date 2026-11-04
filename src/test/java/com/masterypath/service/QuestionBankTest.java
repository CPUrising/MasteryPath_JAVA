package com.masterypath.service;

import com.masterypath.domain.model.KnowledgeType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 题库读写（判分入库、标签、筛选）。
 *
 * <p>标签语义以参考实现 {@code service.py:996-1001} 为准：<b>整体替换</b>、
 * <b>不过滤空值</b>、条目不存在<b>抛错</b>。`0008` 曾把这三条实现成相反行为，
 * 本 change 修正并改写对应断言。
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
    @DisplayName("打标签是整体替换，不是追加（参考实现 entry.tags = list(dict.fromkeys(...))）")
    void taggingReplacesInsteadOfAppending() {
        askAndGrade("q1", "4");
        String entryId = store.listBankEntries("p").get(0).getEntryId();

        service.tagQuestion(entryId, List.of("a", "b"));
        QuestionBankEntry updated = service.tagQuestion(entryId, List.of("b", "c"));

        // 整体替换：第一次的 "a" 不再保留。
        assertThat(updated.getTags()).containsExactly("b", "c");
    }

    @Test
    @DisplayName("同批内重复标签去重且保持首次出现顺序")
    void taggingDeduplicatesWithinBatch() {
        askAndGrade("q1", "4");
        String entryId = store.listBankEntries("p").get(0).getEntryId();

        QuestionBankEntry updated = service.tagQuestion(entryId, List.of("exam", "hard", "exam"));

        assertThat(updated.getTags()).containsExactly("exam", "hard");
    }

    @Test
    @DisplayName("空串与空白标签被保留（参考实现不做校验，不得顺手加固）")
    void blankTagsPreserved() {
        askAndGrade("q1", "4");
        String entryId = store.listBankEntries("p").get(0).getEntryId();

        QuestionBankEntry updated = service.tagQuestion(entryId, List.of("", "  ", "real"));

        assertThat(updated.getTags()).containsExactly("", "  ", "real");
    }

    @Test
    @DisplayName("空列表清空标签（整体替换的自然结果）")
    void emptyListClearsTags() {
        askAndGrade("q1", "4");
        String entryId = store.listBankEntries("p").get(0).getEntryId();
        service.tagQuestion(entryId, List.of("a", "b"));

        QuestionBankEntry updated = service.tagQuestion(entryId, List.of());

        assertThat(updated.getTags()).isEmpty();
    }

    @Test
    @DisplayName("null 标签转成 \"None\"（Python str(None)），与 String.valueOf 的 \"null\" 不同")
    void nullTagBecomesPythonNone() {
        askAndGrade("q1", "4");
        String entryId = store.listBankEntries("p").get(0).getEntryId();

        QuestionBankEntry updated = service.tagQuestion(entryId, List.of("ok"));
        assertThat(updated.getTags()).containsExactly("ok");

        QuestionBankEntry withNull = service.tagQuestion(entryId,
                java.util.Arrays.asList("ok", null));
        assertThat(withNull.getTags()).containsExactly("ok", "None");
    }

    @Test
    @DisplayName("对不存在的条目打标签抛前置条件异常，文案逐字")
    void tagMissingEntryThrows() {
        assertThatThrownBy(() -> service.tagQuestion("nope", List.of("x")))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("unknown question bank entry: nope");
    }

    @Test
    @DisplayName("空白 tag 是真值：参与过滤，返回 0 条（不是返回全部）")
    void blankTagStillFilters() {
        askAndGrade("q1", "4");
        String entryId = store.listBankEntries("p").get(0).getEntryId();
        service.tagQuestion(entryId, List.of("exam"));

        assertThat(service.listQuestions("p", "  ")).isEmpty();
        assertThat(service.listQuestions("p", "exam")).hasSize(1);
    }

    @Test
    @DisplayName("空串与 null 的 tag 是假值：不过滤，返回全部")
    void falsyTagReturnsAll() {
        askAndGrade("q1", "4");

        assertThat(service.listQuestions("p", "")).hasSize(1);
        assertThat(service.listQuestions("p", null)).hasSize(1);
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
