package com.masterypath.adapter.tool;

import com.masterypath.domain.mode.SessionMode;
import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.service.LearningService;
import com.masterypath.storage.LearningStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 状态快照、模式切换、出题与判分工具。
 *
 * <p>对照标准：{@code ../MasteryPath/tests/test_mastery_status.py}。
 */
class StatusAndQuizToolsTest {

    @TempDir
    Path tempDir;

    private LearningStore store;
    private LearningService service;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
        service = new LearningService(store);
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    // -- mastery_status -------------------------------------------------------

    @Test
    @DisplayName("空目标不报 complete，而是 empty + message")
    @SuppressWarnings("unchecked")
    void emptyGoalIsNotComplete() {
        service.createPath("p", "learn X", "X");
        ToolContext ctx = ctx("p", SessionMode.OUTLINE);

        Map<String, Object> payload = payloadOf(
                MasteryTools.all().get("mastery_status").execute(Map.of(), ctx));

        assertThat(payload).containsEntry("status", "empty")
                .containsEntry("goal", "learn X");
        assertThat((Map<String, Object>) payload.get("map")).isEmpty();
        assertThat((Map<String, Object>) payload.get("next")).isEmpty();
        assertThat(String.valueOf(payload.get("message"))).contains("mastery_build");
    }

    @Test
    @DisplayName("活动路径暴露 map / next / mode / name")
    @SuppressWarnings("unchecked")
    void activePathExposesIds() {
        build("p", "X");
        ToolContext ctx = ctx("p", SessionMode.STUDY);

        Map<String, Object> payload = payloadOf(
                MasteryTools.all().get("mastery_status").execute(Map.of(), ctx));

        assertThat(payload).containsEntry("status", "active")
                .containsEntry("mode", "study")
                .containsEntry("name", "X");
        assertThat((Map<String, Object>) payload.get("map")).containsOnlyKeys("k1", "k2");
        assertThat((Map<String, Object>) payload.get("learning_stages"))
                .containsOnlyKeys("k1", "k2")
                .containsEntry("k1", "not_yet_covered");
        assertThat((Map<String, Object>) payload.get("next"))
                .containsEntry("knowledge_point_id", "k1");
    }

    @Test
    @DisplayName("待答题走公共投影，不含 expected_answer / explanation（INV-07）")
    @SuppressWarnings("unchecked")
    void pendingQuestionIsPublic() {
        build("p", "X");
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q1");
        pending.setKnowledgePointId("k1");
        pending.setQuestionType(PendingQuestion.QuestionType.SHORT);
        pending.setPrompt("2+2?");
        pending.setExpectedAnswer("4");
        pending.setExplanation("four");
        service.registerQuestion("p", pending, "s1", "t1");
        ToolContext ctx = ctx("p", SessionMode.STUDY);

        Map<String, Object> payload = payloadOf(
                MasteryTools.all().get("mastery_status").execute(Map.of(), ctx));

        @SuppressWarnings("unchecked")
        Map<String, Object> shown = (Map<String, Object>) payload.get("pending_question");
        assertThat(shown).containsEntry("question_id", "q1")
                .containsEntry("prompt", "2+2?")
                .doesNotContainKeys("expected_answer", "explanation");
        assertThat((Map<String, Object>) payload.get("next"))
                .containsEntry("action", "answer_pending");
    }

    @Test
    @DisplayName("intake_needed 在无画像时为真")
    void intakeNeededWithoutProfile() {
        build("p", "X");
        ToolContext ctx = ctx("p", SessionMode.STUDY);

        Map<String, Object> payload = payloadOf(
                MasteryTools.all().get("mastery_status").execute(Map.of(), ctx));

        assertThat(payload).containsEntry("intake_needed", true);
    }

    // -- mastery_mode ---------------------------------------------------------

    @Test
    @DisplayName("切换模式返回 switched 并持久化到会话")
    void modeSwitchPersists() {
        build("p", "X");
        ToolContext ctx = ctx("p", SessionMode.OUTLINE);

        ToolResult result = MasteryTools.all().get("mastery_mode")
                .execute(Map.of("mode", "study", "reason", "ready"), ctx);

        assertThat(result.ok()).isTrue();
        assertThat(result.metadata()).containsEntry("mode", "study")
                .containsEntry("previous_mode", "outline")
                .containsEntry("persisted", true);
        assertThat(store.getSession("s1")).containsEntry("mode", "study");
    }

    @Test
    @DisplayName("切到同一模式返回 unchanged")
    void sameModeIsUnchanged() {
        build("p", "X");
        ToolContext ctx = ctx("p", SessionMode.STUDY);

        Map<String, Object> body = payloadOf(MasteryTools.all().get("mastery_mode")
                .execute(Map.of("mode", "study"), ctx));

        assertThat(body).containsEntry("status", "unchanged");
    }

    @Test
    @DisplayName("非法模式被拒，文案列出三个模式")
    void invalidModeRefused() {
        ToolContext ctx = ctx("p", SessionMode.STUDY);

        ToolResult result = MasteryTools.all().get("mastery_mode")
                .execute(Map.of("mode", "nope"), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content()).isEqualTo(
                "'nope' is not a mode. Use one of: 'outline', 'study', 'review'.");
    }

    @Test
    @DisplayName("无大纲切 study 被拒（准入）")
    void studyWithoutOutlineRefused() {
        service.createPath("p", "learn X", "X");
        ToolContext ctx = ctx("p", SessionMode.OUTLINE);

        ToolResult result = MasteryTools.all().get("mastery_mode")
                .execute(Map.of("mode", "study"), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content()).contains("no outline to study yet");
    }

    // -- mastery_quiz ---------------------------------------------------------

    @Test
    @DisplayName("出题成功并结束回合，metadata 含 question_card")
    void quizRegistersAndEndsTurn() {
        build("p", "X");
        ToolContext ctx = ctx("p", SessionMode.STUDY);
        boolean[] ended = {false};
        ctx.setEndTurn(() -> ended[0] = true);

        ToolResult result = MasteryTools.all().get("mastery_quiz").execute(Map.of(
                "knowledge_point_id", "k1",
                "question", "2+2?",
                "expected_answer", "4"), ctx);

        assertThat(result.ok()).isTrue();
        assertThat(result.metadata()).containsEntry("status", "registered");
        assertThat(ended[0]).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> card = (Map<String, Object>) result.metadata().get("question_card");
        assertThat(card).containsEntry("objective_name", "fact one")
                .containsEntry("attempt", 1)
                .doesNotContainKeys("expected_answer", "explanation");
    }

    @Test
    @DisplayName("已有待答题时重放同一张卡（INV-02）")
    void quizReplaysOpenCard() {
        build("p", "X");
        ToolContext ctx = ctx("p", SessionMode.STUDY);
        MasteryTools.all().get("mastery_quiz").execute(Map.of(
                "knowledge_point_id", "k1", "question", "2+2?", "expected_answer", "4"), ctx);

        ToolResult second = MasteryTools.all().get("mastery_quiz").execute(Map.of(
                "knowledge_point_id", "k1", "question", "3+3?", "expected_answer", "6"), ctx);

        assertThat(second.ok()).isTrue();
        assertThat(second.metadata()).containsEntry("status", "already_pending");
        assertThat(second.content()).contains("re-presenting it");
    }

    @Test
    @DisplayName("review 下考未掌握目标被拒")
    void quizRefusesUnmasteredInReview() {
        build("p", "X");
        ToolContext ctx = ctx("p", SessionMode.REVIEW);

        ToolResult result = MasteryTools.all().get("mastery_quiz").execute(Map.of(
                "knowledge_point_id", "k1", "question", "q", "expected_answer", "a"), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content()).contains("is not mastered yet; review re-tests proven");
    }

    @Test
    @DisplayName("未知知识点被拒")
    void quizRefusesUnknownObjective() {
        build("p", "X");
        ToolContext ctx = ctx("p", SessionMode.STUDY);

        ToolResult result = MasteryTools.all().get("mastery_quiz").execute(Map.of(
                "knowledge_point_id", "nope", "question", "q", "expected_answer", "a"), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content()).isEqualTo("unknown objective: nope");
    }

    @Test
    @DisplayName("缺 question 被拒")
    void quizRequiresQuestion() {
        build("p", "X");
        ToolContext ctx = ctx("p", SessionMode.STUDY);

        ToolResult result = MasteryTools.all().get("mastery_quiz").execute(Map.of(
                "knowledge_point_id", "k1", "expected_answer", "a"), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content()).isEqualTo("mastery_quiz needs question");
    }

    @Test
    @DisplayName("定性目标出题时提示「判分不会打开它」")
    void quizNotesQualitativeGate() {
        service.createPath("p", "learn X", "X");
        service.build("p", Map.of("modules", List.of(module("M", "deep idea",
                KnowledgeType.CONCEPT))), false);
        ToolContext ctx = ctx("p", SessionMode.STUDY);

        ToolResult result = MasteryTools.all().get("mastery_quiz").execute(Map.of(
                "knowledge_point_id", "k1", "question", "explain", "expected_answer", "x"), ctx);

        assertThat(result.ok()).isTrue();
        assertThat(result.content()).contains("gated qualitatively");
    }

    // -- mastery_grade --------------------------------------------------------

    @Test
    @DisplayName("判分成功返回 Graded")
    void gradeSucceeds() {
        build("p", "X");
        ToolContext ctx = ctx("p", SessionMode.STUDY);
        MasteryTools.all().get("mastery_quiz").execute(Map.of(
                "knowledge_point_id", "k1", "question", "2+2?", "expected_answer", "4"), ctx);
        String questionId = String.valueOf(store.getOpenInteraction("p").getQuestion().getQuestionId());

        ToolResult result = MasteryTools.all().get("mastery_grade")
                .execute(Map.of("question_id", questionId, "answer", "4"), ctx);

        assertThat(result.ok()).isTrue();
        assertThat(result.content()).isEqualTo("Graded: correct.");
    }

    @Test
    @DisplayName("无法解析的选择题判错，而不是拒判")
    void unparseableChoiceIsIncorrect() {
        service.createPath("p", "learn X", "X");
        service.build("p", Map.of("modules", List.of(module("M", "pick one",
                KnowledgeType.MEMORY))), false);
        ToolContext ctx = ctx("p", SessionMode.STUDY);
        MasteryTools.all().get("mastery_quiz").execute(Map.of(
                "knowledge_point_id", "k1",
                "question", "pick",
                "expected_answer", "A",
                "options", List.of(
                        Map.of("label", "A", "body", "first"),
                        Map.of("label", "B", "body", "second"))), ctx);
        String questionId = String.valueOf(store.getOpenInteraction("p").getQuestion().getQuestionId());

        ToolResult result = MasteryTools.all().get("mastery_grade")
                .execute(Map.of("question_id", questionId, "answer", "zzz"), ctx);

        assertThat(result.ok()).isTrue();
        assertThat(result.content()).isEqualTo("Graded: incorrect.");
    }

    @Test
    @DisplayName("未知题号被拒")
    void gradeUnknownQuestionRefused() {
        build("p", "X");
        ToolContext ctx = ctx("p", SessionMode.STUDY);

        ToolResult result = MasteryTools.all().get("mastery_grade")
                .execute(Map.of("question_id", "nope", "answer", "4"), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content()).isEqualTo("unknown question_id: nope");
    }

    // -- 题卡协议 -------------------------------------------------------------

    @Test
    @DisplayName("选项乱序后 expected 跟随正确正文；卡片允许自由文本；缺省题号可判分")
    void shuffledChoiceKeepsExpectedBodyAndAllowsFreeText() {
        build("p", "x");
        ToolContext ctx = ctx("p", SessionMode.STUDY);

        ToolResult quiz = MasteryTools.all().get("mastery_quiz").execute(Map.of(
                "knowledge_point_id", "k1",
                "question", "pick",
                "expected_answer", "B",
                "options", List.of(
                        Map.of("label", "A", "body", "first"),
                        Map.of("label", "B", "body", "second"),
                        Map.of("label", "C", "body", "third"))), ctx);

        assertThat(quiz.ok()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> card = (Map<String, Object>) quiz.metadata().get("question_card");
        assertThat(card).containsEntry("allow_free_text", true);

        PendingQuestion stored = store.getOpenInteraction("p").getQuestion();
        assertThat(stored.getOptions()).extracting(option -> option.getLabel())
                .containsExactly("A", "B", "C");
        String expectedBody = stored.getOptions().stream()
                .filter(option -> option.getLabel().equals(stored.getExpectedAnswer()))
                .findFirst()
                .orElseThrow()
                .getBody();
        assertThat(expectedBody).isEqualTo("second");

        ToolResult graded = MasteryTools.all().get("mastery_grade")
                .execute(Map.of("answer", stored.getExpectedAnswer()), ctx);

        assertThat(graded.ok()).isTrue();
        assertThat(graded.content()).isEqualTo("Graded: correct.");
    }

    @Test
    @DisplayName("mastery_skip_question 无参数时跳过当前待答题")
    void skipWithoutArgumentsClearsOpenQuestion() {
        build("p", "x");
        ToolContext ctx = ctx("p", SessionMode.STUDY);
        MasteryTools.all().get("mastery_quiz").execute(Map.of(
                "knowledge_point_id", "k1",
                "question", "2+2?",
                "expected_answer", "4"), ctx);

        ToolResult skipped = MasteryTools.all().get("mastery_skip_question")
                .execute(Map.of(), ctx);

        assertThat(skipped.ok()).isTrue();
        assertThat(store.getOpenInteraction("p")).isNull();
    }

    // -- 辅助 -----------------------------------------------------------------

    private ToolContext ctx(String pathId, SessionMode mode) {
        ToolContext ctx = new ToolContext(service, pathId);
        ctx.setSessionMode(mode);
        ctx.setSessionId("s1");
        return ctx;
    }

    private void build(String pathId, String name) {
        service.createPath(pathId, "learn " + name, name);
        service.build(pathId, Map.of("modules", List.of(
                module("M", "fact one", KnowledgeType.MEMORY),
                module("M2", "deep idea", KnowledgeType.CONCEPT))), false);
    }

    private static Map<String, Object> module(String name, String kpName, KnowledgeType type) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("name", kpName);
        point.put("type", type.value());
        Map<String, Object> module = new LinkedHashMap<>();
        module.put("name", name);
        module.put("knowledge_points", List.of(point));
        return module;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payloadOf(ToolResult result) {
        try {
            return com.masterypath.domain.model.JsonSupport.objectMapper()
                    .readValue(result.content(), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("tool content is not JSON: " + result.content(), e);
        }
    }
}
