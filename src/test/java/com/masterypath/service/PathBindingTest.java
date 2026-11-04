package com.masterypath.service;

import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningProgress;
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
 * 路径概览、scratch 与会话绑定（{@code ADR-021}，{@code TC-S1}…{@code TC-S4}）。
 *
 * <p>对照标准：{@code ../MasteryPath/tests/test_path_binding.py}。
 * 核心：scratch 靠「空 map」隐藏，不靠标志位。
 */
class PathBindingTest {

    @TempDir
    Path tempDir;

    private LearningStore store;
    private LearningService service;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
        service = new LearningService(store);
        for (String pathId : List.of("p1", "p2")) {
            store.create(ServiceFixtures.onePoint(pathId, KnowledgeType.MEMORY));
        }
        session("s1", "p1");
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    // -- TC-S1 路径概览 -------------------------------------------------------

    @Test
    @DisplayName("TC-S1：列出全部已建路径，按 path_id 升序")
    void listsBuiltPathsSorted() {
        List<Map<String, Object>> rows = service.listPathOverviews();

        assertThat(rows).extracting(row -> row.get("path_id")).containsExactly("p1", "p2");
    }

    @Test
    @DisplayName("TC-S1：概览含 9 个键")
    void overviewHasNineKeys() {
        Map<String, Object> row = service.listPathOverviews().get(0);

        assertThat(row).containsOnlyKeys("path_id", "name", "goal", "objectives", "mastered",
                "learning", "not_started", "due_reviews", "owner_session_id");
    }

    @Test
    @DisplayName("TC-S1：objectives 与 mastered 反映地图状态")
    void overviewCountsReflectMap() {
        Map<String, Object> row = service.listPathOverviews().get(0);

        assertThat(row).containsEntry("objectives", 1).containsEntry("mastered", 0)
                .containsEntry("not_started", 1);
    }

    @Test
    @DisplayName("TC-S1：空 map 的路径被跳过（scratch 隐藏的唯一机制）")
    void emptyMapPathsHidden() {
        store.create(new LearningProgress("empty"));

        assertThat(service.listPathOverviews())
                .extracting(row -> row.get("path_id"))
                .containsExactly("p1", "p2");
    }

    @Test
    @DisplayName("TC-S1：name 为空时回退到 path_id")
    void nameFallsBackToPathId() {
        LearningProgress blank = ServiceFixtures.onePoint("blank", KnowledgeType.MEMORY);
        blank.setName("");
        store.create(blank);

        Map<String, Object> row = service.listPathOverviews().stream()
                .filter(item -> "blank".equals(item.get("path_id")))
                .findFirst()
                .orElseThrow();

        assertThat(row).containsEntry("name", "blank");
    }

    // -- TC-S2 scratch --------------------------------------------------------

    @Test
    @DisplayName("TC-S2：scratch id 由 session_id 派生，owner 为会话")
    void scratchDerivedFromSession() {
        LearningProgress scratch = service.ensureScratchPath("s1");

        assertThat(scratch.getPathId()).isEqualTo("scratch-s1");
        assertThat(scratch.getOwnerSessionId()).isEqualTo("s1");
        assertThat(scratch.getName()).isEqualTo("Scratch");
    }

    @Test
    @DisplayName("TC-S2：非法字符替换为下划线，首尾下划线被剥离")
    void scratchSanitisesSessionId() {
        assertThat(service.ensureScratchPath("a b").getPathId()).isEqualTo("scratch-a_b");
        assertThat(service.ensureScratchPath("_x_").getPathId()).isEqualTo("scratch-x");
    }

    @Test
    @DisplayName("TC-S2：全部非法字符时兜底 default")
    void scratchFallsBackToDefault() {
        assertThat(service.ensureScratchPath("!!!").getPathId()).isEqualTo("scratch-default");
    }

    @Test
    @DisplayName("TC-S2：建档发 path.created，payload 含 scratch=true")
    void scratchEmitsCreatedEvent() {
        service.ensureScratchPath("s1");

        String payload = store.eventsSince("scratch-s1", 0).stream()
                .filter(event -> "path.created".equals(event.get("event_type")))
                .map(event -> String.valueOf(event.get("payload")))
                .findFirst()
                .orElseThrow();
        assertThat(payload).contains("\"name\":\"Scratch\"")
                .contains("\"scratch\":true")
                .contains("\"goal\":\"\"");
    }

    @Test
    @DisplayName("TC-S2：已存在且无 owner 时认领，且不发事件")
    void scratchClaimsOwnerWithoutEvent() {
        store.create(new LearningProgress("scratch-s1"));
        int before = store.eventsSince("scratch-s1", 0).size();

        LearningProgress claimed = service.ensureScratchPath("s1");

        assertThat(claimed.getOwnerSessionId()).isEqualTo("s1");
        assertThat(store.eventsSince("scratch-s1", 0)).hasSize(before);
    }

    @Test
    @DisplayName("TC-S2：重复调用返回同一路径，不重复建档")
    void scratchIsIdempotent() {
        LearningProgress first = service.ensureScratchPath("s1");
        int eventsAfterFirst = store.eventsSince("scratch-s1", 0).size();

        LearningProgress second = service.ensureScratchPath("s1");

        assertThat(second.getPathId()).isEqualTo(first.getPathId());
        assertThat(store.eventsSince("scratch-s1", 0)).hasSize(eventsAfterFirst);
    }

    @Test
    @DisplayName("TC-S2：空 scratch 不出现在路径概览里")
    void emptyScratchHiddenFromOverview() {
        String scratchId = service.ensureScratchPath("s1").getPathId();

        assertThat(service.listPathOverviews())
                .extracting(row -> row.get("path_id"))
                .doesNotContain(scratchId);
    }

    // -- TC-S3 会话绑定 -------------------------------------------------------

    @Test
    @DisplayName("TC-S3：绑定把会话指向新路径")
    void bindMovesSession() {
        service.bindSessionPath("s1", "p2");

        assertThat(store.getSession("s1")).containsEntry("path_id", "p2");
    }

    @Test
    @DisplayName("TC-S3：空 session_id 返回 null 且不写库")
    void bindEmptySessionIsNoop() {
        assertThat(service.bindSessionPath("", "p2")).isNull();
        assertThat(service.bindSessionPath(null, "p2")).isNull();
        assertThat(store.getSession("s1")).containsEntry("path_id", "p1");
    }

    @Test
    @DisplayName("TC-S3：绑定不存在的会话返回 null")
    void bindUnknownSessionReturnsNull() {
        assertThat(service.bindSessionPath("nope", "p2")).isNull();
    }

    // -- TC-S4 删除会话 -------------------------------------------------------

    @Test
    @DisplayName("TC-S4：删除会话时一并删除自有的空 scratch")
    void deleteSessionRemovesOwnedEmptyScratch() {
        String scratchId = service.ensureScratchPath("s1").getPathId();
        service.bindSessionPath("s1", scratchId);
        assertThat(store.load(scratchId)).isNotNull();

        assertThat(service.deleteSession("s1")).isTrue();

        assertThat(store.getSession("s1")).isNull();
        assertThat(store.load(scratchId)).isNull();
    }

    @Test
    @DisplayName("TC-S4：路径有知识点时不删除路径")
    void deleteSessionKeepsPathWithKnowledgePoints() {
        service.bindSessionPath("s1", "p1");

        assertThat(service.deleteSession("s1")).isTrue();

        assertThat(store.getSession("s1")).isNull();
        assertThat(store.load("p1")).isNotNull();
    }

    @Test
    @DisplayName("TC-S4：路径不属于该会话时不删除路径")
    void deleteSessionKeepsForeignPath() {
        store.create(new LearningProgress("scratch-other"));
        store.mutate("scratch-other", tx -> {
            tx.progress().setOwnerSessionId("other");
            return tx.progress();
        });
        service.bindSessionPath("s1", "scratch-other");

        service.deleteSession("s1");

        assertThat(store.load("scratch-other")).isNotNull();
    }

    @Test
    @DisplayName("TC-S4：会话不存在时返回 false")
    void deleteUnknownSessionReturnsFalse() {
        assertThat(service.deleteSession("nope")).isFalse();
    }

    @Test
    @DisplayName("TC-S4：路径还有其他会话时不删除路径")
    void deleteSessionKeepsPathWithOtherSessions() {
        String scratchId = service.ensureScratchPath("s1").getPathId();
        service.bindSessionPath("s1", scratchId);
        session("s2", scratchId);

        service.deleteSession("s1");

        assertThat(store.load(scratchId)).isNotNull();
    }

    // -- 转发 ----------------------------------------------------------------

    @Test
    @DisplayName("listSessions 返回该路径的会话")
    void listSessionsForwards() {
        assertThat(service.listSessions("p1")).hasSize(1);
        assertThat(service.listSessions("p2")).isEmpty();
    }

    @Test
    @DisplayName("getSession 返回会话，不存在返回 null")
    void getSessionForwards() {
        assertThat(service.getSession("s1")).containsEntry("session_id", "s1");
        assertThat(service.getSession("nope")).isNull();
    }

    // -- 辅助 ----------------------------------------------------------------

    private void session(String sessionId, String pathId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("session_id", sessionId);
        row.put("path_id", pathId);
        row.put("mode", "study");
        row.put("title", sessionId);
        store.upsertSession(row);
    }
}
