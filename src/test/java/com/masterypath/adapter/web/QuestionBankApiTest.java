package com.masterypath.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;
import com.masterypath.domain.model.QuestionBankEntry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 题库工作台 REST 契约（{@code 0046}）。 */
@Tag("api")
class QuestionBankApiTest {

    private static final ObjectMapper MAPPER = JsonSupport.objectMapper();

    private ApiTestSupport api;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport();
        api.buildOutline("p");
        save("e1", "short", "4", "incorrect", 3.0, List.of("java", "并发"));
        save("e2", "short", "4", "correct", 2.0, List.of("java"));
        save("e3", "written", "", "partial", 1.0, List.of("essay"));
    }

    @AfterEach
    void tearDown() {
        api.close();
    }

    @Test
    @DisplayName("检索按多标签（AND）过滤并附带 path_name 与答案")
    void searchFiltersAndKeepsManagementView() throws Exception {
        List<Map<String, Object>> rows = jsonList(api.mvc.perform(
                get("/questions/search").param("tags", "java,并发")).andReturn());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("entry_id", "e1")
                .containsEntry("path_name", "X")
                .containsEntry("correct_answer", "4");
    }

    @Test
    @DisplayName("刷题队列隐藏答案并排除不可刷题")
    void practiceHidesAnswers() throws Exception {
        List<Map<String, Object>> cards = jsonList(api.mvc.perform(
                get("/questions/practice")).andReturn());

        assertThat(cards).hasSize(2);
        assertThat(cards).allSatisfy(card -> assertThat(card).doesNotContainKeys(
                "correct_answer", "explanation", "user_answer", "is_correct"));
        assertThat(cards).extracting(card -> card.get("entry_id"))
                .containsExactly("e1", "e2");
    }

    @Test
    @DisplayName("作答返回 graded 并回写题库条目")
    void attemptGraded() throws Exception {
        Map<String, Object> outcome = json(api.mvc.perform(
                        post("/questions/e1/attempt")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"answer\":\"4\"}"))
                .andReturn());

        assertThat(outcome)
                .containsEntry("status", "graded")
                .containsEntry("result", "correct")
                .containsEntry("is_correct", true)
                .containsEntry("correct_answer", "4");
        assertThat(api.store.getBankEntry("e1").getResult()).isEqualTo("correct");
    }

    @Test
    @DisplayName("无法判定时返回 ungraded 且不写库")
    void attemptUngraded() throws Exception {
        Map<String, Object> outcome = json(api.mvc.perform(
                        post("/questions/e1/attempt")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"answer\":\"  \"}"))
                .andReturn());

        assertThat(outcome).containsEntry("status", "ungraded");
        assertThat(api.store.getBankEntry("e1").getUserAnswer()).isEmpty();
    }

    @Test
    @DisplayName("未知条目 404、不可刷题 422")
    void attemptErrors() throws Exception {
        assertThat(api.mvc.perform(post("/questions/nope/attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"x\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(api.mvc.perform(post("/questions/e3/attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"x\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("标签汇总返回计数")
    void tagSummary() throws Exception {
        List<Map<String, Object>> rows = jsonList(api.mvc.perform(get("/questions/tags")).andReturn());

        assertThat(rows).extracting(row -> row.get("tag"), row -> row.get("count"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("java", 2),
                        org.assertj.core.groups.Tuple.tuple("essay", 1),
                        org.assertj.core.groups.Tuple.tuple("并发", 1));
    }

    @Test
    @DisplayName("重命名 / 合并 / 删除 / 批量打标生效")
    void tagMutations() throws Exception {
        assertThat(json(api.mvc.perform(post("/questions/tags/rename")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"并发\",\"to\":\"java\"}"))
                .andReturn())).containsEntry("updated", 1);
        assertThat(api.store.getBankEntry("e1").getTags()).containsExactly("java");

        assertThat(json(api.mvc.perform(post("/questions/tags/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":[\"java\",\"essay\"],\"to\":\"重点\"}"))
                .andReturn())).containsEntry("updated", 3);

        Map<String, Object> batch = json(api.mvc.perform(post("/questions/tags/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entry_ids\":[\"e2\"],\"add\":[\"exam\"],\"remove\":[]}"))
                .andReturn());
        assertThat((List<?>) batch.get("updated")).hasSize(1);
        assertThat(api.store.getBankEntry("e2").getTags()).containsExactly("重点", "exam");

        assertThat(json(api.mvc.perform(post("/questions/tags/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tag\":\"重点\"}"))
                .andReturn())).containsEntry("updated", 3);
        assertThat(api.store.getBankEntry("e2").getTags()).containsExactly("exam");
    }

    @Test
    @DisplayName("空白标签名 422")
    void blankTagIs422() throws Exception {
        assertThat(api.mvc.perform(post("/questions/tags/rename")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"\",\"to\":\"x\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
        assertThat(api.mvc.perform(post("/questions/tags/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entry_ids\":[],\"add\":[],\"remove\":[]}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    // -- 辅助 -----------------------------------------------------------------

    @Test
    @DisplayName("按知识库分组：多归属两边都计数，未关联单独成组")
    void kbGroups() throws Exception {
        api.service.createPath("kbp1", "goal one", "K1");
        bindKnowledgeBases("kbp1", "kb1", "kb2");
        saveOn("kbp1", "kbe1", 2.0);
        api.service.createPath("kbp2", "goal two", "K2");
        saveOn("kbp2", "kbe2", 1.0);

        List<Map<String, Object>> multi = jsonList(api.mvc.perform(
                get("/questions/kb-groups").param("path_id", "kbp1")).andReturn());
        assertThat(multi).extracting(row -> row.get("knowledge_base_id"), row -> row.get("total"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("kb1", 1),
                        org.assertj.core.groups.Tuple.tuple("kb2", 1));

        List<Map<String, Object>> unassigned = jsonList(api.mvc.perform(
                get("/questions/kb-groups").param("path_id", "kbp2")).andReturn());
        assertThat(unassigned).extracting(row -> row.get("knowledge_base_id"), row -> row.get("total"))
                .containsExactly(org.assertj.core.groups.Tuple.tuple("", 1));
    }

    @Test
    @DisplayName("检索按知识库 OR 过滤，并返回归属列表")
    void searchFiltersByKnowledgeBase() throws Exception {
        api.service.createPath("kbp1", "goal one", "K1");
        bindKnowledgeBases("kbp1", "kb1");
        saveOn("kbp1", "kbe1", 2.0);
        api.service.createPath("kbp2", "goal two", "K2");
        bindKnowledgeBases("kbp2", "kb2");
        saveOn("kbp2", "kbe2", 1.0);

        List<Map<String, Object>> both = jsonList(api.mvc.perform(
                get("/questions/search").param("knowledge_base_ids", "kb1,kb2")).andReturn());
        assertThat(both).extracting(row -> row.get("entry_id"))
                .containsExactlyInAnyOrder("kbe1", "kbe2");

        List<Map<String, Object>> onlyKb1 = jsonList(api.mvc.perform(
                get("/questions/search").param("knowledge_base_ids", "kb1")).andReturn());
        assertThat(onlyKb1).hasSize(1);
        assertThat(onlyKb1.get(0)).containsEntry("entry_id", "kbe1");
        assertThat((List<?>) onlyKb1.get(0).get("knowledge_bases")).hasSize(1);

        List<Map<String, Object>> missing = jsonList(api.mvc.perform(
                get("/questions/search").param("knowledge_base_ids", "kb9")).andReturn());
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("刷题队列支持知识库过滤，卡片含归属且隐藏答案")
    void practiceFiltersByKnowledgeBase() throws Exception {
        api.service.createPath("kbp1", "goal one", "K1");
        bindKnowledgeBases("kbp1", "kb1");
        saveOn("kbp1", "kbe1", 2.0);
        api.service.createPath("kbp2", "goal two", "K2");
        bindKnowledgeBases("kbp2", "kb2");
        saveOn("kbp2", "kbe2", 1.0);

        List<Map<String, Object>> cards = jsonList(api.mvc.perform(
                get("/questions/practice").param("knowledge_base_ids", "kb2")).andReturn());

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0))
                .containsEntry("entry_id", "kbe2")
                .doesNotContainKeys("correct_answer", "explanation", "user_answer", "is_correct");
        assertThat((List<?>) cards.get(0).get("knowledge_bases")).hasSize(1);
    }

    @Test
    @DisplayName("参考实现端点 GET /questions 不新增知识库字段")
    void legacyQuestionsEndpointUnchanged() throws Exception {
        List<Map<String, Object>> rows = jsonList(api.mvc.perform(get("/questions")).andReturn());

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> assertThat(row)
                .doesNotContainKey("knowledge_bases")
                .doesNotContainKey("practiceable"));
    }

    // -- 辅助 -----------------------------------------------------------------

    private void bindKnowledgeBases(String pathId, String... kbIds) {
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
        api.service.setKnowledgeBaseSources(pathId, sources);
    }

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
        entry.setExplanation("because " + entryId);
        entry.setResult("correct");
        entry.setCorrect(true);
        entry.setCreatedAt(createdAt);
        api.store.saveBankEntry(entry);
    }

    private void save(String entryId, String type, String answer, String result,
                      double createdAt, List<String> tags) {
        QuestionBankEntry entry = new QuestionBankEntry();
        entry.setEntryId(entryId);
        entry.setPathId("p");
        entry.setKnowledgePointId("k1");
        entry.setKnowledgePointName("fact one");
        entry.setQuestionId("q-" + entryId);
        entry.setQuestion("prompt " + entryId);
        entry.setQuestionType(type);
        entry.setCorrectAnswer(answer);
        entry.setExplanation("because " + entryId);
        entry.setResult(result);
        entry.setCreatedAt(createdAt);
        entry.setTags(new ArrayList<>(tags));
        api.store.saveBankEntry(entry);
    }

    private static Map<String, Object> json(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> jsonList(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), List.class);
    }
}
