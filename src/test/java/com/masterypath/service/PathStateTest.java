package com.masterypath.service;

import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.storage.LearningStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 路径状态快照（参考实现 {@code get_state}）。
 *
 * <p>核心：19 个键齐全（已用真实 Python 探针核对）；{@code pending_question} 必须走白名单投影
 * （{@code INV-07}）。
 */
class PathStateTest {

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

    @Test
    @DisplayName("路径不存在时返回 missing")
    void missingPathReturnsMissing() {
        Map<String, Object> state = service.getState("nope");

        assertThat(state).containsEntry("status", "missing").containsEntry("path_id", "nope");
        assertThat(state).hasSize(2);
    }

    @Test
    @DisplayName("活动路径返回 active 与全部 21 个键")
    void activePathHasAllKeys() {
        Map<String, Object> state = service.getState("p");

        assertThat(state).containsOnlyKeys(
                "status", "path_id", "name", "goal", "version", "map", "counts", "next",
                "learning_stages", "sources", "knowledge_bases", "gaps", "proposals", "coverage",
                "reviews", "review_settings", "session_count", "learner_profile",
                "pending_question", "last_answered", "due_reviews");
        assertThat(state).containsEntry("status", "active").containsEntry("path_id", "p");
    }

    @Test
    @DisplayName("learning_stages 按知识点给出阶段")
    void learningStagesCoverEveryObjective() {
        Map<String, Object> state = service.getState("p");

        @SuppressWarnings("unchecked")
        Map<String, Object> stages = (Map<String, Object>) state.get("learning_stages");
        assertThat(stages).containsEntry("k1", "not_yet_covered");
    }

    @Test
    @DisplayName("从未作答时 last_answered 为 none")
    @SuppressWarnings("unchecked")
    void lastAnsweredIsNoneBeforeAnyAnswer() {
        Map<String, Object> last = (Map<String, Object>) service.getState("p").get("last_answered");

        assertThat(last).containsEntry("status", "none");
    }

    @Test
    @DisplayName("last_answered 回放上一次作答的题号、原文与判分")
    @SuppressWarnings("unchecked")
    void lastAnsweredReplaysTheLatestAttempt() {
        register("q1", "4");
        service.grade("p", "q1", "4", 0, false, "s1", "t1");

        Map<String, Object> last = (Map<String, Object>) service.getState("p").get("last_answered");

        assertThat(last).containsEntry("status", "answered")
                .containsEntry("question_id", "q1")
                .containsEntry("answer", "4")
                .containsEntry("result", "correct")
                .containsEntry("session_id", "s1")
                .containsEntry("turn_id", "t1");
        assertThat(((Number) last.get("mastery_after")).doubleValue()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("attempt.graded 事件带上 session_id / turn_id")
    void gradedEventCarriesSessionAndTurn() {
        register("q1", "4");
        service.grade("p", "q1", "4", 0, false, "s1", "t1");

        Map<String, Object> event = store.eventsSince("p", 0).stream()
                .filter(item -> "attempt.graded".equals(item.get("event_type")))
                .findFirst()
                .orElseThrow();
        assertThat(event).containsEntry("session_id", "s1").containsEntry("turn_id", "t1");
    }

    @Test
    @DisplayName("counts 汇总 new/learning/mastered 且 total 与 map 条目数一致")
    void countsAggregatesStatuses() {
        Map<String, Object> state = service.getState("p");

        Map<?, ?> counts = (Map<?, ?>) state.get("counts");
        assertThat(counts.get("new")).isEqualTo(1);
        assertThat(counts.get("mastered")).isEqualTo(0);
        assertThat(counts.get("learning")).isEqualTo(0);
        assertThat(counts.get("total")).isEqualTo(1);
    }

    @Test
    @DisplayName("掌握后 counts.mastered 递增、next 变为 complete")
    void masteredCountAndNext() {
        masterK1();

        Map<String, Object> state = service.getState("p");

        assertThat(((Map<?, ?>) state.get("counts")).get("mastered")).isEqualTo(1);
        assertThat(((Map<?, ?>) state.get("next")).get("action")).isEqualTo("complete");
    }

    @Test
    @DisplayName("reviews 含派生的 due / interval_index / provenance")
    void reviewsCarryDerivedFields() {
        masterK1();

        List<?> reviews = (List<?>) service.getState("p").get("reviews");

        assertThat(reviews).hasSize(1);
        Map<?, ?> task = (Map<?, ?>) reviews.get(0);
        assertThat(task.get("knowledge_point_id")).isEqualTo("k1");
        assertThat(task.get("knowledge_point_name")).isEqualTo("fact one");
        assertThat(task.get("due")).isEqualTo(false);
        assertThat(task.get("interval_index")).isEqualTo(1);
        assertThat(task.get("provenance")).isEqualTo("system");
    }

    @Test
    @DisplayName("INV-07：pending_question 投影恰好 7 个键，不含 expected_answer")
    void pendingQuestionProjectionHidesAnswer() {
        register("q1", "4");

        Map<String, Object> state = service.getState("p");

        @SuppressWarnings("unchecked")
        Map<String, Object> pending = (Map<String, Object>) state.get("pending_question");
        assertThat(pending).containsOnlyKeys("question_id", "knowledge_point_id", "module_id",
                "prompt", "question_type", "options", "difficulty");
        assertThat(pending).doesNotContainKeys("expected_answer", "explanation");
    }

    @Test
    @DisplayName("无待答题时 pending_question 为 null")
    void noPendingQuestionIsNull() {
        assertThat(service.getState("p").get("pending_question")).isNull();
    }

    @Test
    @DisplayName("review_settings 返回 desired_retention 与 scope")
    void reviewSettingsShape() {
        Map<?, ?> settings = (Map<?, ?>) service.getState("p").get("review_settings");

        assertThat(settings.get("desired_retention")).isEqualTo(0.9);
        assertThat(settings.get("scope")).isEqualTo("path");
    }

    @Test
    @DisplayName("session_count 反映该路径的会话数")
    void sessionCountReflectsSessions() {
        assertThat(service.getState("p").get("session_count")).isEqualTo(0);

        store.upsertSession(new java.util.LinkedHashMap<>(Map.of(
                "session_id", "s1", "path_id", "p", "title", "first")));

        assertThat(service.getState("p").get("session_count")).isEqualTo(1);
    }

    @Test
    @DisplayName("learner_profile 为空时为 null，写入后可读出")
    void learnerProfileExposed() {
        assertThat(service.getState("p").get("learner_profile")).isNull();

        service.recordLearnerProfile("p",
                new java.util.LinkedHashMap<>(Map.of("notes", "prefers examples")), "", "");

        @SuppressWarnings("unchecked")
        Map<String, Object> profile =
                (Map<String, Object>) service.getState("p").get("learner_profile");
        assertThat(profile).containsEntry("notes", "prefers examples");
    }

    @Test
    @DisplayName("map 条目含 status / source / mastery / type")
    void mapEntriesShape() {
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> map =
                (Map<String, Map<String, Object>>) service.getState("p").get("map");

        Map<String, Object> entry = map.get("k1");
        assertThat(entry).containsKeys("name", "module_id", "module_name", "type",
                "status", "source", "mastery");
        assertThat(entry).containsEntry("status", "new");
    }

    /** 连续答对 3 次，跨过门槛（DeepTutor 置信上限要求至少 3 次）。 */
    private void masterK1() {
        for (int index = 0; index < 3; index++) {
            register("q" + index, "4");
            service.grade("p", "q" + index, "4", 0, false, "", "");
        }
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
}
