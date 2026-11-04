package com.masterypath.adapter.tool;

import com.masterypath.domain.mode.SessionMode;
import com.masterypath.domain.model.KnowledgeType;
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
 * 工具层：门控、异常转换、条件挂载（{@code ADR-005} / {@code ADR-023}）。
 *
 * <p>对照标准：{@code ../MasteryPath/tests/test_loop.py} 的 2 个真实工具用例、
 * {@code test_repair_profile.py} 的工具层 2 例、{@code test_mastery_nav.py} 的工具层 2 例。
 */
class MasteryToolsTest {

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

    // -- 注册表 ---------------------------------------------------------------

    @Test
    @DisplayName("注册表含 23 个工具，且全部通过 fail-closed 归类校验")
    void registryIsComplete() {
        assertThat(MasteryTools.all()).hasSize(23);
        assertThat(MasteryTools.all().keySet()).contains(
                "mastery_status", "mastery_mode", "mastery_build", "mastery_revise",
                "mastery_quiz", "mastery_grade", "mastery_repair_question", "mastery_profile",
                "mastery_assess", "mastery_paths", "mastery_switch", "mastery_leave",
                "mastery_topics", "mastery_sessions", "mastery_open_session",
                "mastery_new_session", "rag", "read_source", "mastery_note_explained",
                "mastery_defer_objective", "mastery_propose_objective", "mastery_note_gap",
                "mastery_skip_question");
    }

    // -- 门控 -----------------------------------------------------------------

    @Test
    @DisplayName("study 下 mastery_build 被拒，且地图不变")
    void buildRefusedInStudy() {
        ToolContext ctx = ctx("p", SessionMode.STUDY);

        ToolResult result = MasteryTools.all().get("mastery_build")
                .execute(Map.of("modules", List.of()), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content()).contains("mastery_mode");
        assertThat(service.getState("p")).containsEntry("status", "missing");
    }

    @Test
    @DisplayName("outline 下 mastery_quiz 被拒")
    void quizRefusedInOutline() {
        ToolContext ctx = ctx("p", SessionMode.OUTLINE);

        ToolResult result = MasteryTools.all().get("mastery_quiz")
                .execute(Map.of("knowledge_point_id", "k1", "question", "q",
                        "expected_answer", "a"), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content()).contains("mastery_mode");
    }

    @Test
    @DisplayName("未记录模式时放行全部工具（INV-09）")
    void unrecordedModeEnforcesNothing() {
        ToolContext ctx = ctx("p", null);

        ToolResult result = MasteryTools.all().get("mastery_build")
                .execute(Map.of("modules", List.of(module("M", "fact one"))), ctx);

        assertThat(result.ok()).isTrue();
    }

    @Test
    @DisplayName("共享工具在任意模式下放行")
    void sharedToolsAlwaysAllowed() {
        for (SessionMode mode : SessionMode.values()) {
            ToolContext ctx = ctx("p", mode);
            assertThat(MasteryTools.all().get("mastery_status").execute(Map.of(), ctx).ok())
                    .as("mastery_status in " + mode.value()).isTrue();
            assertThat(MasteryTools.all().get("mastery_paths").execute(Map.of(), ctx).ok())
                    .as("mastery_paths in " + mode.value()).isTrue();
        }
    }

    // -- 异常转换 -------------------------------------------------------------

    @Test
    @DisplayName("修复工具：非法 action 转成 ok=false 而非抛异常")
    void repairToolConvertsException() {
        ToolContext ctx = ctx("p", SessionMode.STUDY);

        ToolResult result = MasteryTools.all().get("mastery_repair_question")
                .execute(Map.of("action", "nope", "reason", "why"), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content()).isEqualTo("action must be 'void' or 'correct'");
    }

    @Test
    @DisplayName("修复工具：缺 reason 转成 ok=false")
    void repairToolRequiresReason() {
        ToolContext ctx = ctx("p", SessionMode.STUDY);

        ToolResult result = MasteryTools.all().get("mastery_repair_question")
                .execute(Map.of("action", "void"), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content())
                .isEqualTo("a reason is required so the repair can be audited");
    }

    @Test
    @DisplayName("画像工具：部分字段可写入，返回 updated_fields")
    @SuppressWarnings("unchecked")
    void profileToolRecordsPartialFields() {
        service.createPath("p", "learn X", "X");
        ToolContext ctx = ctx("p", SessionMode.STUDY);

        ToolResult result = MasteryTools.all().get("mastery_profile")
                .execute(Map.of("prior_knowledge", "knows algebra"), ctx);

        assertThat(result.ok()).isTrue();
        assertThat(result.content()).isEqualTo("Recorded the learner profile.");
        assertThat((List<String>) result.metadata().get("updated_fields"))
                .containsExactly("prior_knowledge");
    }

    @Test
    @DisplayName("画像工具：全空字段被拒")
    void profileToolRejectsEmpty() {
        ToolContext ctx = ctx("p", SessionMode.STUDY);

        ToolResult result = MasteryTools.all().get("mastery_profile").execute(Map.of(), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content()).startsWith("mastery_profile needs at least one of");
    }

    // -- 条件挂载 -------------------------------------------------------------

    @Test
    @DisplayName("无知识库不挂 rag，无来源不挂 read_source")
    void conditionalMounting() {
        ToolContext bare = ctx("p", SessionMode.STUDY);

        Map<String, Tool> mounted = MasteryTools.mountedTools(bare);

        assertThat(mounted).doesNotContainKeys("rag", "read_source");
        assertThat(mounted).hasSize(MasteryTools.all().size() - 2);
    }

    @Test
    @DisplayName("有知识库与来源时两者都挂载")
    void mountsSourceToolsWhenInputsExist() {
        ToolContext ctx = ctx("p", SessionMode.STUDY);
        ctx.setKbIds(List.of("kb1"));
        ctx.setSourceIndex(Map.of("src:a", "text"));

        Map<String, Tool> mounted = MasteryTools.mountedTools(ctx);

        assertThat(mounted).containsKeys("rag", "read_source");
    }

    @Test
    @DisplayName("条件挂载不改全局注册表")
    void mountingDoesNotMutateRegistry() {
        ToolContext bare = ctx("p", SessionMode.STUDY);

        MasteryTools.mountedTools(bare);

        assertThat(MasteryTools.all()).containsKeys("rag", "read_source");
    }

    // -- 导航工具 -------------------------------------------------------------

    @Test
    @DisplayName("打开别的课程的会话被拒")
    void openSessionFromAnotherTopicRefused() {
        buildPath("p1", "Statistics");
        buildPath("p2", "Machine Learning");
        session("s1", "p1", "Lesson one");
        ToolContext ctx = ctx("p1", SessionMode.STUDY);

        ToolResult result = MasteryTools.all().get("mastery_open_session")
                .execute(Map.of("path_id", "p2", "session_id", "s1"), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content()).contains("has no conversation 's1'");
    }

    @Test
    @DisplayName("同一课程新建会话被拒")
    void newSessionOnSameTopicRefused() {
        buildPath("p1", "Statistics");
        ToolContext ctx = ctx("p1", SessionMode.STUDY);

        ToolResult result = MasteryTools.all().get("mastery_new_session")
                .execute(Map.of("path_id", "p1"), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content()).contains("already the study session for that topic");
    }

    @Test
    @DisplayName("导航工具：空 path_id 与未知课程文案逐字")
    void navFailureMessages() {
        ToolContext ctx = ctx("p1", SessionMode.STUDY);

        assertThat(MasteryTools.all().get("mastery_sessions")
                .execute(Map.of(), ctx).content())
                .isEqualTo("path_id is required; call mastery_topics for the ids.");
        assertThat(MasteryTools.all().get("mastery_sessions")
                .execute(Map.of("path_id", "nope"), ctx).content())
                .isEqualTo("No mastery topic 'nope' exists (or it has no map yet). "
                        + "Call mastery_topics for the topics you can send the learner to.");
    }

    // -- 捕获工具 -------------------------------------------------------------

    @Test
    @DisplayName("mastery_propose_objective 写入候选目标且不改地图")
    void proposeObjectiveRecordsCandidate() {
        buildPath("p1", "Statistics");
        ToolContext ctx = ctx("p1", SessionMode.STUDY);

        ToolResult result = MasteryTools.all().get("mastery_propose_objective")
                .execute(Map.of("name", "Bayes theorem", "type", "concept",
                        "reason", "missing prerequisite"), ctx);

        assertThat(result.ok()).isTrue();
        assertThat(result.metadata()).containsEntry("status", "pending");
        assertThat(service.store().load("p1").getProposals()).hasSize(1);
        // 不改地图：知识点数量不变（INV-53）。
        assertThat(service.store().load("p1").getModules().get(0).getKnowledgePoints()).hasSize(1);
    }

    @Test
    @DisplayName("mastery_propose_objective 名字过短被拒")
    void proposeObjectiveRequiresName() {
        buildPath("p1", "Statistics");
        ToolContext ctx = ctx("p1", SessionMode.STUDY);

        ToolResult result = MasteryTools.all().get("mastery_propose_objective")
                .execute(Map.of("name", "x"), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content()).contains("2+ characters");
    }

    @Test
    @DisplayName("mastery_note_gap 写入缺口且不改地图")
    void noteGapRecordsGap() {
        buildPath("p1", "Statistics");
        ToolContext ctx = ctx("p1", SessionMode.STUDY);
        int before = service.store().load("p1").getSourceGaps().size();

        ToolResult result = MasteryTools.all().get("mastery_note_gap")
                .execute(Map.of("kind", "goal_gap", "ref", "k1", "reason", "no source"), ctx);

        assertThat(result.ok()).isTrue();
        assertThat(result.metadata()).containsKey("gap_id");
        assertThat(service.store().load("p1").getSourceGaps()).hasSize(before + 1);
    }

    @Test
    @DisplayName("mastery_note_gap 非法 kind 被拒")
    void noteGapRejectsUnknownKind() {
        buildPath("p1", "Statistics");
        ToolContext ctx = ctx("p1", SessionMode.STUDY);

        ToolResult result = MasteryTools.all().get("mastery_note_gap")
                .execute(Map.of("kind", "banana"), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content()).isEqualTo("kind must be goal_gap or source_omission");
    }

    // -- 辅助 -----------------------------------------------------------------

    private ToolContext ctx(String pathId, SessionMode mode) {
        ToolContext ctx = new ToolContext(service, pathId);
        ctx.setSessionMode(mode);
        ctx.setSessionId("s1");
        return ctx;
    }

    private void buildPath(String pathId, String name) {
        service.createPath(pathId, "learn " + name, name);
        service.build(pathId, Map.of("modules", List.of(module(name + " basics", name + " fact"))),
                false);
    }

    private static Map<String, Object> module(String name, String kpName) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("name", kpName);
        point.put("type", KnowledgeType.MEMORY.value());
        Map<String, Object> module = new LinkedHashMap<>();
        module.put("name", name);
        module.put("knowledge_points", List.of(point));
        return module;
    }

    private void session(String sessionId, String pathId, String title) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("session_id", sessionId);
        row.put("path_id", pathId);
        row.put("title", title);
        row.put("mode", "study");
        store.upsertSession(row);
    }
}
