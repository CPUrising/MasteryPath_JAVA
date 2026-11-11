package com.masterypath.service;

import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningProgress;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 题库工作台：检索、标签管理与刷题（{@code 0046}）。 */
class QuestionBankPracticeServiceTest {

    @TempDir
    Path tempDir;

    private LearningStore store;
    private LearningService learning;
    private QuestionBankService bank;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
        learning = new LearningService(store);
        bank = new QuestionBankService(store);
        store.create(ServiceFixtures.onePoint("p", KnowledgeType.MEMORY));
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    // -- 检索 -----------------------------------------------------------------

    @Test
    @DisplayName("多标签为 AND 语义")
    void tagsAreAnd() {
        save("e1", "short", "4", "correct", 3.0, "k1", "fact one", List.of("java", "并发"));
        save("e2", "short", "4", "correct", 2.0, "k1", "fact one", List.of("java"));

        assertThat(bank.list("", List.of("java"), "", "", List.of()))
                .extracting(QuestionBankEntry::getEntryId).containsExactly("e1", "e2");
        assertThat(bank.list("", List.of("java", "并发"), "", "", List.of()))
                .extracting(QuestionBankEntry::getEntryId).containsExactly("e1");
    }

    @Test
    @DisplayName("result=wrong 覆盖 incorrect / partial / voided")
    void wrongResultAlias() {
        save("e1", "short", "4", "correct", 4.0, "k1", "fact one", List.of());
        save("e2", "short", "4", "incorrect", 3.0, "k1", "fact one", List.of());
        save("e3", "short", "4", "partial", 2.0, "k1", "fact one", List.of());
        save("e4", "short", "4", "voided", 1.0, "k1", "fact one", List.of());

        assertThat(bank.list("", List.of(), "wrong", "", List.of()))
                .extracting(QuestionBankEntry::getEntryId).containsExactly("e2", "e3", "e4");
    }

    @Test
    @DisplayName("关键字同时匹配题干与知识点名")
    void keywordMatchesQuestionAndPoint() {
        save("e1", "short", "4", "correct", 2.0, "k1", "facts", List.of());
        save("e2", "short", "4", "correct", 1.0, "k2", "linear algebra", List.of());

        assertThat(bank.list("", List.of(), "", "FACT", List.of()))
                .extracting(QuestionBankEntry::getEntryId).containsExactly("e1");
        assertThat(bank.list("", List.of(), "", "algebra", List.of()))
                .extracting(QuestionBankEntry::getEntryId).containsExactly("e2");
    }

    @Test
    @DisplayName("标签汇总忽略空白并按数量降序")
    void tagSummaryCounts() {
        save("e1", "short", "4", "correct", 3.0, "k1", "a", List.of("java", "并发"));
        save("e2", "short", "4", "correct", 2.0, "k1", "a", List.of("java"));
        save("e3", "short", "4", "correct", 1.0, "k1", "a", List.of("", " ", "java"));

        assertThat(bank.tagSummary(""))
                .extracting(row -> row.get("tag"), row -> row.get("count"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("java", 3),
                        org.assertj.core.groups.Tuple.tuple("并发", 1));
    }

    // -- 标签管理 -------------------------------------------------------------

    @Test
    @DisplayName("重命名在工作区全量生效并自然合并重复标签")
    void renameTag() {
        save("e1", "short", "4", "correct", 2.0, "k1", "a", List.of("a", "b"));
        save("e2", "short", "4", "correct", 1.0, "k1", "a", List.of("b"));

        assertThat(bank.renameTag("a", "b")).isEqualTo(1);
        assertThat(store.getBankEntry("e1").getTags()).containsExactly("b");
        assertThat(store.getBankEntry("e2").getTags()).containsExactly("b");
    }

    @Test
    @DisplayName("合并多个标签到目标标签")
    void mergeTags() {
        save("e1", "short", "4", "correct", 2.0, "k1", "a", List.of("b", "c"));
        save("e2", "short", "4", "correct", 1.0, "k1", "a", List.of("d"));

        assertThat(bank.mergeTags(List.of("b", "c"), "x")).isEqualTo(1);
        assertThat(store.getBankEntry("e1").getTags()).containsExactly("x");
        assertThat(store.getBankEntry("e2").getTags()).containsExactly("d");
    }

    @Test
    @DisplayName("删除标签清空所有命中条目")
    void deleteTag() {
        save("e1", "short", "4", "correct", 2.0, "k1", "a", List.of("temp", "keep"));
        save("e2", "short", "4", "correct", 1.0, "k1", "a", List.of("temp"));

        assertThat(bank.deleteTag("temp")).isEqualTo(2);
        assertThat(store.getBankEntry("e1").getTags()).containsExactly("keep");
        assertThat(store.getBankEntry("e2").getTags()).isEmpty();
    }

    @Test
    @DisplayName("批量打标先加后删，且未知条目抛错")
    void batchTag() {
        save("e1", "short", "4", "correct", 2.0, "k1", "a", List.of("draft"));
        save("e2", "short", "4", "correct", 1.0, "k1", "a", List.of());

        List<QuestionBankEntry> updated = bank.batchTag(
                List.of("e1", "e2"), List.of("重点"), List.of("draft"));

        assertThat(updated).hasSize(2);
        assertThat(store.getBankEntry("e1").getTags()).containsExactly("重点");
        assertThat(store.getBankEntry("e2").getTags()).containsExactly("重点");
        assertThatThrownBy(() -> bank.batchTag(List.of("nope"), List.of("x"), List.of()))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("unknown question bank entry: nope");
    }

    @Test
    @DisplayName("空白标签名以 IllegalArgumentException 拒绝")
    void blankTagRejected() {
        assertThatThrownBy(() -> bank.renameTag("", "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tag name must not be blank");
        assertThatThrownBy(() -> bank.batchTag(List.of(), List.of("x"), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("entry_ids must not be empty");
    }

    // -- 刷题队列 -------------------------------------------------------------

    @Test
    @DisplayName("队列排除不可刷题，数量夹取 1–50")
    void practiceQueueFiltersAndClamps() {
        save("e1", "short", "4", "correct", 3.0, "k1", "a", List.of());
        save("e2", "short", "4", "incorrect", 2.0, "k1", "a", List.of());
        save("e3", "written", "", "partial", 1.0, "k1", "a", List.of());

        assertThat(bank.practiceQueue("", List.of(), List.of(), 10, "newest"))
                .extracting(QuestionBankEntry::getEntryId).containsExactly("e1", "e2");
        assertThat(bank.practiceQueue("", List.of(), List.of(), 1, "newest")).hasSize(1);
        assertThat(bank.practiceQueue("", List.of(), List.of(), 100, "newest")).hasSize(2);
        assertThat(bank.practiceQueue("", List.of(), List.of(), 0, "newest")).hasSize(2);
    }

    @Test
    @DisplayName("错题优先把未答对的排在最前")
    void practiceQueueWrongFirst() {
        save("e1", "short", "4", "correct", 3.0, "k1", "a", List.of());
        save("e2", "short", "4", "incorrect", 2.0, "k1", "a", List.of());

        assertThat(bank.practiceQueue("", List.of(), List.of(), 10, "wrong_first"))
                .extracting(QuestionBankEntry::getEntryId).containsExactly("e2", "e1");
    }

    // -- 作答 -----------------------------------------------------------------

    @Test
    @DisplayName("作答只回写最近一次结果并释放答案")
    void attemptWritesBack() {
        save("e1", "short", "4", "incorrect", 1.0, "k1", "a", List.of());

        Map<String, Object> outcome = bank.attempt("e1", "4");

        assertThat(outcome)
                .containsEntry("status", "graded")
                .containsEntry("result", "correct")
                .containsEntry("is_correct", true)
                .containsEntry("correct_answer", "4");
        QuestionBankEntry stored = store.getBankEntry("e1");
        assertThat(stored.getUserAnswer()).isEqualTo("4");
        assertThat(stored.isCorrect()).isTrue();
        assertThat(stored.getResult()).isEqualTo("correct");
    }

    @Test
    @DisplayName("ungraded 零写入")
    void ungradedWritesNothing() {
        save("e1", "short", "4", "correct", 1.0, "k1", "a", List.of());
        QuestionBankEntry before = store.getBankEntry("e1");
        before.setUserAnswer("previous");
        store.saveBankEntry(before);

        Map<String, Object> outcome = bank.attempt("e1", "   ");

        assertThat(outcome).containsEntry("status", "ungraded");
        QuestionBankEntry stored = store.getBankEntry("e1");
        assertThat(stored.getUserAnswer()).isEqualTo("previous");
        assertThat(stored.getResult()).isEqualTo("correct");
    }

    @Test
    @DisplayName("未知条目抛前置条件异常；不可刷题以 IllegalArgumentException 拒绝")
    void attemptRejectsUnknownAndUnpracticeable() {
        save("e1", "written", "", "partial", 1.0, "k1", "a", List.of());

        assertThatThrownBy(() -> bank.attempt("nope", "x"))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("unknown question bank entry: nope");
        assertThatThrownBy(() -> bank.attempt("e1", "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("question is not practiceable: e1");
    }

    @Test
    @DisplayName("刷题不影响掌握闭环：版本、掌握度、待答题、复习状态与事件都不变")
    void attemptLeavesMasteryUntouched() {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q1");
        pending.setKnowledgePointId("k1");
        pending.setModuleId("m1");
        pending.setPrompt("2+2?");
        pending.setQuestionType(PendingQuestion.QuestionType.SHORT);
        pending.setExpectedAnswer("4");
        learning.registerQuestion("p", pending, "", "");
        learning.grade("p", "q1", "4", 0, false, "", "");

        QuestionBankEntry entry = store.listBankEntries("p").get(0);
        LearningProgress beforeState = store.load("p");
        Map<String, Double> masteryBefore = new HashMap<>(beforeState.getMasteryLevels());
        int versionBefore = beforeState.getVersion();
        int eventsBefore = store.eventsSince("p", 0).size();
        int repetitionsBefore = beforeState.getRepetitionStates().size();

        bank.attempt(entry.getEntryId(), "4");

        LearningProgress afterState = store.load("p");
        assertThat(afterState.getVersion()).isEqualTo(versionBefore);
        assertThat(afterState.getMasteryLevels()).isEqualTo(masteryBefore);
        assertThat(afterState.getPendingQuestion()).isNull();
        assertThat(afterState.getRepetitionStates()).hasSize(repetitionsBefore);
        assertThat(store.eventsSince("p", 0)).hasSize(eventsBefore);
    }

    // -- 知识库归属（0047） ----------------------------------------------------

    @Test
    @DisplayName("按知识库筛选为 OR：命中任一即返回")
    void listFiltersByKnowledgeBaseOr() {
        bindKnowledgeBases("p", "kb1");
        store.create(ServiceFixtures.onePoint("p2", KnowledgeType.MEMORY));
        bindKnowledgeBases("p2", "kb2");
        store.create(ServiceFixtures.onePoint("p3", KnowledgeType.MEMORY));
        saveOn("p", "e1", 3.0);
        saveOn("p2", "e2", 2.0);
        saveOn("p3", "e3", 1.0);

        assertThat(bank.list("", List.of(), "", "", List.of()))
                .extracting(QuestionBankEntry::getEntryId)
                .containsExactlyInAnyOrder("e1", "e2", "e3");
        assertThat(bank.list("", List.of(), "", "", List.of("kb1")))
                .extracting(QuestionBankEntry::getEntryId).containsExactly("e1");
        assertThat(bank.list("", List.of(), "", "", List.of("kb1", "kb2")))
                .extracting(QuestionBankEntry::getEntryId)
                .containsExactlyInAnyOrder("e1", "e2");
    }

    @Test
    @DisplayName("分组统计多归属：一条路径绑两个库时两边都计数")
    void kbGroupsCountsMultiMembership() {
        bindKnowledgeBases("p", "kb1", "kb2");
        store.create(ServiceFixtures.onePoint("p2", KnowledgeType.MEMORY));
        bindKnowledgeBases("p2", "kb1");
        saveOn("p", "e1", 2.0);
        saveOn("p2", "e2", 1.0);

        assertThat(bank.kbGroups(""))
                .extracting(row -> row.get("knowledge_base_id"), row -> row.get("total"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("kb1", 2),
                        org.assertj.core.groups.Tuple.tuple("kb2", 1));
    }

    @Test
    @DisplayName("未关联题目单独成组且排在最后")
    void kbGroupsHasUnassignedLast() {
        bindKnowledgeBases("p", "kb1");
        store.create(ServiceFixtures.onePoint("p2", KnowledgeType.MEMORY));
        saveOn("p", "e1", 2.0);
        saveOn("p2", "e2", 1.0);

        assertThat(bank.kbGroups(""))
                .extracting(row -> row.get("knowledge_base_id"), row -> row.get("total"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("kb1", 1),
                        org.assertj.core.groups.Tuple.tuple("", 1));
    }

    @Test
    @DisplayName("知识库归属不写入题库 data")
    void knowledgeBasesAreNotPersisted() throws Exception {
        bindKnowledgeBases("p", "kb1");
        saveOn("p", "e1", 1.0);

        QuestionBankEntry enriched = bank.list("", List.of(), "", "", List.of()).get(0);
        assertThat(enriched.getKnowledgeBases())
                .extracting(com.masterypath.domain.model.KnowledgeBaseRef::id)
                .containsExactly("kb1");

        String data = com.masterypath.domain.model.JsonSupport.objectMapper()
                .writeValueAsString(store.getBankEntry("e1"));
        assertThat(data).doesNotContain("knowledge_bases");
    }

    // -- 工厂 -----------------------------------------------------------------

    /** 给路径绑定知识库（写入 `sources[]` 的 `kind=knowledge_base` 条目）。 */
    private void bindKnowledgeBases(String pathId, String... kbIds) {
        store.mutate(pathId, tx -> {
            List<com.masterypath.domain.model.TopicSource> sources = new ArrayList<>();
            for (String kbId : kbIds) {
                com.masterypath.domain.model.TopicSource source =
                        new com.masterypath.domain.model.TopicSource();
                source.setId("s-" + kbId);
                source.setKind("knowledge_base");
                source.setSourceId(kbId);
                source.setLabel("KB " + kbId);
                sources.add(source);
            }
            tx.progress().setSources(sources);
            return null;
        }, null, true);
    }

    /** 在指定路径写一条可刷的 short 题。 */
    private void saveOn(String pathId, String entryId, double createdAt) {
        QuestionBankEntry entry = new QuestionBankEntry();
        entry.setEntryId(entryId);
        entry.setPathId(pathId);
        entry.setKnowledgePointId("k1");
        entry.setKnowledgePointName("fact one");
        entry.setQuestionId("q-" + entryId);
        entry.setQuestion("prompt " + entryId);
        entry.setQuestionType("short");
        entry.setCorrectAnswer("4");
        entry.setResult("correct");
        entry.setCorrect(true);
        entry.setCreatedAt(createdAt);
        store.saveBankEntry(entry);
    }

    /** 直接写一条题库条目；createdAt 显式给定以保证排序可断言。 */
    private void save(String entryId, String type, String answer, String result, double createdAt,
                      String kpId, String kpName, List<String> tags) {
        QuestionBankEntry entry = new QuestionBankEntry();
        entry.setEntryId(entryId);
        entry.setPathId("p");
        entry.setKnowledgePointId(kpId);
        entry.setKnowledgePointName(kpName);
        entry.setQuestionId("q-" + entryId);
        entry.setQuestion("prompt " + entryId);
        entry.setQuestionType(type);
        entry.setCorrectAnswer(answer);
        entry.setExplanation("because " + entryId);
        entry.setResult(result);
        entry.setCorrect("correct".equals(result));
        entry.setCreatedAt(createdAt);
        entry.setTags(new ArrayList<>(tags));
        if ("choice".equals(type)) {
            entry.setOptions(choiceOptions(answer));
        }
        store.saveBankEntry(entry);
    }

    private static List<Map<String, Object>> choiceOptions(String answer) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (String label : List.of("A", "B")) {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("label", label);
            option.put("body", label.equals(answer) ? "right" : "wrong");
            options.add(option);
        }
        return options;
    }
}
