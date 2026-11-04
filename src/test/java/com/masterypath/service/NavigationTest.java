package com.masterypath.service;

import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningModule;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.storage.LearningStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 只读导航与 hand-off 卡片（{@code ADR-022}）。
 *
 * <p>对照标准：{@code ../MasteryPath/tests/test_mastery_nav.py} 与
 * {@code masterypath/learning/navigation.py}。
 * 核心：本层<b>从不写库</b>；条件键「不出现」而不是「值为 0」。
 */
class NavigationTest {

    @TempDir
    Path tempDir;

    private LearningStore store;
    private NavigationService nav;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
        nav = new NavigationService(store);
        store.create(path("p1", "Statistics", "learn Statistics", "Statistics basics"));
        store.create(path("p2", "Machine Learning", "learn Machine Learning",
                "Machine Learning basics"));
        session("s1", "p1", "Lesson one", 4, "we were here", Map.of());
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    // -- topicCards -----------------------------------------------------------

    @Test
    @DisplayName("列出全部已建课程，含模块大纲与进度")
    void listsBuiltTopics() {
        Map<String, Object> payload = nav.topicCards("");

        assertThat(ids(payload)).containsExactlyInAnyOrder("p1", "p2");
        assertThat(payload).containsEntry("total_topics", 2).doesNotContainKey("topics_omitted");
        Map<String, Object> first = firstTopic(payload, "p1");
        assertThat(first).containsEntry("name", "Statistics")
                .containsEntry("objectives", 1)
                .containsEntry("sessions", 1);
        assertThat((List<?>) first.get("modules")).hasSize(1);
    }

    @Test
    @DisplayName("query 按名称/目标/模块名子串过滤，大小写不敏感")
    void queryFiltersByHaystack() {
        assertThat(ids(nav.topicCards("statistics"))).containsExactly("p1");
        assertThat(ids(nav.topicCards("MACHINE"))).containsExactly("p2");
        assertThat(ids(nav.topicCards("learn machine"))).containsExactly("p2");
        assertThat(ids(nav.topicCards("basics"))).containsExactlyInAnyOrder("p1", "p2");
        assertThat(ids(nav.topicCards("nothing here"))).isEmpty();
    }

    @Test
    @DisplayName("haystack 不含 path_id：按 p1 搜不到名为 Statistics 的路径")
    void haystackExcludesPathId() {
        assertThat(ids(nav.topicCards("p1"))).isEmpty();
    }

    @Test
    @DisplayName("query 键只在原始 query 为真值时出现，且值为原始 query")
    void queryKeyOnlyWhenTruthy() {
        assertThat(nav.topicCards("")).doesNotContainKey("query");
        assertThat(nav.topicCards(null)).doesNotContainKey("query");
        // 空白是真值：不过滤（needle 为空），但 query 键出现且保留原值。
        Map<String, Object> blank = nav.topicCards("  ");
        assertThat(blank).containsEntry("query", "  ");
        assertThat(ids(blank)).containsExactlyInAnyOrder("p1", "p2");
    }

    @Test
    @DisplayName("空 map 的路径不出现在课程卡片里")
    void emptyMapPathHidden() {
        store.create(new LearningProgress("empty"));

        assertThat(ids(nav.topicCards(""))).containsExactlyInAnyOrder("p1", "p2");
    }

    @Test
    @DisplayName("模块超过 40 个时截断并给出 modules_omitted")
    void modulesTruncated() {
        LearningProgress wide = new LearningProgress("wide");
        wide.setName("Wide");
        List<LearningModule> modules = new ArrayList<>();
        for (int index = 0; index < 45; index++) {
            modules.add(ServiceFixtures.module("m" + index, "M" + index, index,
                    List.of(ServiceFixtures.kp("k" + index, "fact " + index,
                            KnowledgeType.MEMORY))));
        }
        wide.setModules(modules);
        store.create(wide);

        Map<String, Object> card = firstTopic(nav.topicCards(""), "wide");

        assertThat((List<?>) card.get("modules")).hasSize(NavigationService.MODULE_LIMIT);
        assertThat(card).containsEntry("modules_omitted", 5);
    }

    @Test
    @DisplayName("未超限时没有 modules_omitted 键（不是 0）")
    void noModulesOmittedKeyWhenUnderLimit() {
        assertThat(firstTopic(nav.topicCards(""), "p1")).doesNotContainKey("modules_omitted");
    }

    @Test
    @DisplayName("课程超过 20 个时截断并给出 topics_omitted，total_topics 为截断前总数")
    void topicsTruncated() {
        for (int index = 0; index < 25; index++) {
            store.create(path("x" + index, "X" + index, "goal", "X" + index + " basics"));
        }

        Map<String, Object> payload = nav.topicCards("");

        assertThat((List<?>) payload.get("topics")).hasSize(NavigationService.TOPIC_LIMIT);
        assertThat(payload).containsEntry("total_topics", 27).containsEntry("topics_omitted", 7);
    }

    // -- findTopic ------------------------------------------------------------

    @Test
    @DisplayName("findTopic 返回单条课程，modules 不截断")
    void findTopicReturnsCard() {
        Map<String, Object> topic = nav.findTopic("p1");

        assertThat(topic).containsEntry("path_id", "p1").containsEntry("name", "Statistics");
        assertThat((List<?>) topic.get("modules")).hasSize(1);
    }

    @Test
    @DisplayName("findTopic 对空 map 的路径返回 null")
    void findTopicEmptyMapIsNull() {
        store.create(new LearningProgress("empty"));

        assertThat(nav.findTopic("empty")).isNull();
    }

    @Test
    @DisplayName("findTopic 对空/空白/不存在的 path_id 返回 null")
    void findTopicMissingIsNull() {
        assertThat(nav.findTopic("")).isNull();
        assertThat(nav.findTopic(null)).isNull();
        assertThat(nav.findTopic("   ")).isNull();
        assertThat(nav.findTopic("nope")).isNull();
    }

    @Test
    @DisplayName("findTopic 先 strip 再查库")
    void findTopicStripsPathId() {
        assertThat(nav.findTopic("  p1  ")).containsEntry("path_id", "p1");
    }

    // -- resolveModule --------------------------------------------------------

    @Test
    @DisplayName("resolveModule 按 module_id 匹配")
    void resolveModuleById() {
        Map<String, Object> topic = nav.findTopic("p1");

        assertThat(nav.resolveModule(topic, "m1")).containsEntry("name", "Statistics basics");
    }

    @Test
    @DisplayName("resolveModule 按名称匹配，大小写不敏感")
    void resolveModuleByName() {
        Map<String, Object> topic = nav.findTopic("p1");

        assertThat(nav.resolveModule(topic, "statistics BASICS"))
                .containsEntry("module_id", "m1");
    }

    @Test
    @DisplayName("resolveModule 按 1 基序号匹配，容忍尾部句点")
    void resolveModuleByOrder() {
        Map<String, Object> topic = nav.findTopic("p1");

        assertThat(nav.resolveModule(topic, "1")).containsEntry("module_id", "m1");
        assertThat(nav.resolveModule(topic, "1.")).containsEntry("module_id", "m1");
        assertThat(nav.resolveModule(topic, "2")).isNull();
    }

    @Test
    @DisplayName("resolveModule 对空 ref / 无匹配返回 null")
    void resolveModuleNoMatch() {
        Map<String, Object> topic = nav.findTopic("p1");

        assertThat(nav.resolveModule(topic, "")).isNull();
        assertThat(nav.resolveModule(topic, "   ")).isNull();
        assertThat(nav.resolveModule(topic, "nope")).isNull();
        assertThat(nav.resolveModule(null, "m1")).isNull();
    }

    // -- topicSessions --------------------------------------------------------

    @Test
    @DisplayName("列出会话：标题、消息数、最后消息")
    void listsSessions() {
        Map<String, Object> payload = nav.topicSessions("p1");

        assertThat(payload).containsEntry("total_sessions", 1).doesNotContainKey("sessions_omitted");
        Map<String, Object> row = firstSession(payload, "s1");
        assertThat(row).containsEntry("title", "Lesson one")
                .containsEntry("messages", 4)
                .containsEntry("last_message", "we were here")
                .containsEntry("running", false)
                .containsEntry("awaiting_answer", false);
    }

    @Test
    @DisplayName("跳过 preferences.archived 为真值的会话（含字符串 \"true\"）")
    void skipsArchivedSessions() {
        session("s2", "p1", "Archived", 1, "old", Map.of("archived", true));
        session("s3", "p1", "Archived string", 1, "old", Map.of("archived", "true"));

        Map<String, Object> payload = nav.topicSessions("p1");

        assertThat(sessionIds(payload)).containsExactly("s1");
        assertThat(payload).containsEntry("total_sessions", 1);
    }

    @Test
    @DisplayName("awaiting_answer 需要路径有 pending 且该会话 has_pending_question")
    void awaitingAnswerIsComposite() {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q1");
        store.mutate("p1", tx -> {
            tx.progress().setPendingQuestion(pending);
            return tx.progress();
        });
        session("s2", "p1", "Waiting", 2, "answer me", Map.of(), true);

        Map<String, Object> payload = nav.topicSessions("p1");

        assertThat(firstSession(payload, "s2")).containsEntry("awaiting_answer", true);
        // s1 没有 has_pending_question，即使路径有 pending 也不算在等作答。
        assertThat(firstSession(payload, "s1")).containsEntry("awaiting_answer", false);
    }

    @Test
    @DisplayName("路径没有 pending 时，即使会话 has_pending_question 也不算在等作答")
    void awaitingAnswerNeedsPathPending() {
        session("s2", "p1", "Waiting", 2, "answer me", Map.of(), true);

        assertThat(firstSession(nav.topicSessions("p1"), "s2"))
                .containsEntry("awaiting_answer", false);
    }

    @Test
    @DisplayName("last_message 截断到 240 字符")
    void lastMessageTruncated() {
        String longText = "x".repeat(300);
        session("s2", "p1", "Long", 1, longText, Map.of());

        Map<String, Object> row = firstSession(nav.topicSessions("p1"), "s2");

        assertThat(String.valueOf(row.get("last_message"))).hasSize(240);
    }

    @Test
    @DisplayName("会话超过 20 个时截断，total_sessions 为截断前总数")
    void sessionsTruncated() {
        for (int index = 0; index < 24; index++) {
            session("extra" + index, "p1", "S" + index, 1, "m", Map.of());
        }

        Map<String, Object> payload = nav.topicSessions("p1");

        assertThat((List<?>) payload.get("sessions")).hasSize(NavigationService.SESSION_LIMIT);
        assertThat(payload).containsEntry("total_sessions", 25).containsEntry("sessions_omitted", 5);
    }

    @Test
    @DisplayName("路径不存在时返回空列表")
    void sessionsForMissingPath() {
        Map<String, Object> payload = nav.topicSessions("nope");

        assertThat((List<?>) payload.get("sessions")).isEmpty();
        assertThat(payload).containsEntry("total_sessions", 0);
    }

    // -- handoff --------------------------------------------------------------

    @Test
    @DisplayName("handoff 含 17 个键，emoji 恒为空")
    void handoffHasSeventeenKeys() {
        Map<String, Object> topic = nav.findTopic("p1");
        Map<String, Object> module = nav.resolveModule(topic, "1");
        Map<String, Object> session = firstSession(nav.topicSessions("p1"), "s1");

        Map<String, Object> card = nav.handoff("open", topic, module, "take me back",
                "continue", session);

        assertThat(card).containsOnlyKeys("kind", "path_id", "path_name", "emoji",
                "session_id", "session_title", "session_messages", "session_updated_at",
                "session_awaiting", "session_running", "module_id", "module_name",
                "opening_message", "reason", "due_reviews", "mastered", "objectives");
        assertThat(card).containsEntry("kind", "open")
                .containsEntry("path_id", "p1")
                .containsEntry("path_name", "Statistics")
                .containsEntry("emoji", "")
                .containsEntry("session_id", "s1")
                .containsEntry("session_title", "Lesson one")
                .containsEntry("session_messages", 4)
                .containsEntry("module_id", "m1")
                .containsEntry("module_name", "Statistics basics")
                .containsEntry("opening_message", "take me back")
                .containsEntry("reason", "continue")
                .containsEntry("objectives", 1);
    }

    @Test
    @DisplayName("handoff 在 session/module 为 null 时取默认值")
    void handoffDefaults() {
        Map<String, Object> topic = nav.findTopic("p1");

        Map<String, Object> card = nav.handoff("new", topic, null, null, null, null);

        assertThat(card).containsEntry("kind", "new")
                .containsEntry("session_id", "")
                .containsEntry("session_title", "")
                .containsEntry("session_messages", 0)
                .containsEntry("session_updated_at", 0.0)
                .containsEntry("session_awaiting", false)
                .containsEntry("session_running", false)
                .containsEntry("module_id", "")
                .containsEntry("module_name", "")
                .containsEntry("opening_message", "")
                .containsEntry("reason", "");
    }

    @Test
    @DisplayName("导航层不写库：调用前后路径与会话不变")
    void navigationIsReadOnly() {
        int pathsBefore = store.listPaths().size();
        int sessionsBefore = store.listSessions("p1").size();

        Map<String, Object> topic = nav.findTopic("p1");
        nav.topicCards("statistics");
        nav.resolveModule(topic, "1");
        nav.topicSessions("p1");
        nav.handoff("open", topic, null, "", "", null);

        assertThat(store.listPaths()).hasSize(pathsBefore);
        assertThat(store.listSessions("p1")).hasSize(sessionsBefore);
    }

    // -- 辅助 -----------------------------------------------------------------

    private static LearningProgress path(String pathId, String name, String goal,
                                         String moduleName) {
        LearningProgress progress = new LearningProgress(pathId);
        progress.setName(name);
        progress.setGoal(goal);
        KnowledgePoint point = ServiceFixtures.kp("k-" + pathId, name + " fact",
                KnowledgeType.MEMORY);
        progress.setModules(List.of(ServiceFixtures.module("m1", moduleName, 0, List.of(point))));
        return progress;
    }

    private void session(String sessionId, String pathId, String title, int messages,
                         String lastMessage, Map<String, Object> preferences) {
        session(sessionId, pathId, title, messages, lastMessage, preferences, false);
    }

    private void session(String sessionId, String pathId, String title, int messages,
                         String lastMessage, Map<String, Object> preferences,
                         boolean hasPendingQuestion) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("session_id", sessionId);
        row.put("path_id", pathId);
        row.put("title", title);
        row.put("mode", "study");
        row.put("message_count", messages);
        row.put("last_message", lastMessage);
        row.put("has_pending_question", hasPendingQuestion);
        row.put("preferences", preferences);
        store.upsertSession(row);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> topics(Map<String, Object> payload) {
        return (List<Map<String, Object>>) payload.get("topics");
    }

    private static List<String> ids(Map<String, Object> payload) {
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> topic : topics(payload)) {
            ids.add(String.valueOf(topic.get("path_id")));
        }
        return ids;
    }

    private static Map<String, Object> firstTopic(Map<String, Object> payload, String pathId) {
        return topics(payload).stream()
                .filter(topic -> pathId.equals(topic.get("path_id")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> sessions(Map<String, Object> payload) {
        return (List<Map<String, Object>>) payload.get("sessions");
    }

    private static List<String> sessionIds(Map<String, Object> payload) {
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> row : sessions(payload)) {
            ids.add(String.valueOf(row.get("session_id")));
        }
        return ids;
    }

    private static Map<String, Object> firstSession(Map<String, Object> payload, String sessionId) {
        return sessions(payload).stream()
                .filter(row -> sessionId.equals(row.get("session_id")))
                .findFirst()
                .orElseThrow();
    }
}
