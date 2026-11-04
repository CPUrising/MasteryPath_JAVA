package com.masterypath.service;

import com.masterypath.domain.model.CoverageReport;
import com.masterypath.domain.model.GapKind;
import com.masterypath.domain.model.GapResolution;
import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.SourceGap;
import com.masterypath.domain.model.TopicSource;
import com.masterypath.domain.outline.BuildResult;
import com.masterypath.domain.outline.OutlineException;
import com.masterypath.domain.outline.ReviseResult;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 缺口分类与消解（{@code TC-F2} / {@code TC-F3} / {@code TC-F4}），走真实服务与事务。
 */
class GapClassificationTest {

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

    private static Map<String, Object> kp(String name, String type, List<String> sources) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("name", name);
        point.put("type", type);
        if (sources != null) {
            point.put("sources", sources);
        }
        return point;
    }

    private static Map<String, Object> module(String name, List<String> materials,
                                              Map<String, Object>... kps) {
        Map<String, Object> module = new LinkedHashMap<>();
        module.put("name", name);
        if (materials != null) {
            module.put("materials", materials);
        }
        module.put("knowledge_points", List.of(kps));
        return module;
    }

    @SafeVarargs
    private static Map<String, Object> payload(Map<String, Object>... modules) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("modules", List.of(modules));
        return payload;
    }

    private static TopicSource source(String id, String label) {
        TopicSource source = new TopicSource();
        source.setId(id);
        source.setLabel(label);
        return source;
    }

    // -- TC-F2 ----------------------------------------------------------------

    @Test
    @DisplayName("TC-F2：重新生成后来源仍缺失，缺口依然存在")
    void regenerateDoesNotCloseGap() {
        Map<String, Object> spec = payload(module("A", null, kp("no source", "concept", null)));
        service.build("p", spec, false);

        BuildResult rebuilt = service.build("p", spec, true);

        assertThat(rebuilt.gaps()).isNotEmpty();
        assertThat(rebuilt.gaps()).allMatch(gap -> gap.getKind() == GapKind.GOAL_GAP);
    }

    // -- TC-F3 ----------------------------------------------------------------

    @Test
    @DisplayName("TC-F3：模块 materials 使用来源后，source_omission 闭合")
    void sourceOmissionClosesWhenUsed() {
        service.attachSources("p", List.of(source("s1", "a.txt")));

        BuildResult first = service.build("p",
                payload(module("A", null, kp("obj", "concept", List.of()))), false);
        assertThat(first.gaps())
                .anyMatch(gap -> gap.getKind() == GapKind.SOURCE_OMISSION);

        BuildResult second = service.build("p",
                payload(module("A", List.of("a.txt"), kp("obj", "concept", null))), true);

        assertThat(second.gaps())
                .noneMatch(gap -> gap.getKind() == GapKind.SOURCE_OMISSION);
    }

    // -- TC-F4 ----------------------------------------------------------------

    @Test
    @DisplayName("TC-F4：接受缺口后标记 accepted，且发 gap.resolved")
    void acceptGapThenResolved() {
        BuildResult built = service.build("p",
                payload(module("A", null, kp("no source", "concept", null))), false);
        String gapId = built.gaps().get(0).getId();

        SourceGap resolved = service.resolveGap("p", gapId, "accept");

        assertThat(resolved.getResolution()).isEqualTo(GapResolution.ACCEPTED);
        assertThat(store.load("p").getSourceGaps().get(0).getResolution())
                .isEqualTo(GapResolution.ACCEPTED);
        assertThat(store.eventsSince("p", 0))
                .extracting(event -> event.get("event_type"))
                .contains("gap.resolved");
    }

    @Test
    @DisplayName("TC-F4：接受缺口后该知识点仍为未接地")
    void acceptedGapLeavesUngrounded() {
        BuildResult built = service.build("p",
                payload(module("A", null, kp("no source", "concept", null))), false);
        service.resolveGap("p", built.gaps().get(0).getId(), "accept");

        LearningProgress loaded = store.load("p");
        KnowledgePoint point = loaded.getModules().get(0).getKnowledgePoints().get(0);
        assertThat(point.getSourceIds()).isEmpty();
    }

    @Test
    @DisplayName("regenerate 标记为 regenerated")
    void regenerateMarksResolution() {
        BuildResult built = service.build("p",
                payload(module("A", null, kp("no source", "concept", null))), false);

        SourceGap gap = service.resolveGap("p", built.gaps().get(0).getId(), "regenerate");

        assertThat(gap.getResolution()).isEqualTo(GapResolution.REGENERATED);
    }

    @Test
    @DisplayName("未知 gap_id 被拒绝")
    void unknownGapRejected() {
        service.build("p", payload(module("A", null, kp("no source", "concept", null))), false);

        assertThatThrownBy(() -> service.resolveGap("p", "g99", "accept"))
                .isInstanceOf(OutlineException.class)
                .hasMessageContaining("unknown gap_id");
    }

    @Test
    @DisplayName("未知 action 被拒绝")
    void unknownActionRejected() {
        BuildResult built = service.build("p",
                payload(module("A", null, kp("no source", "concept", null))), false);

        assertThatThrownBy(() -> service.resolveGap("p", built.gaps().get(0).getId(), "ignore"))
                .isInstanceOf(OutlineException.class)
                .hasMessageContaining("unknown gap action");
    }

    // -- 服务层事件 -----------------------------------------------------------

    @Test
    @DisplayName("首次 build 发 outline.built")
    void firstBuildEmitsOutlineBuilt() {
        service.build("p", payload(module("A", null, kp("alpha", "memory", null))), false);

        assertThat(store.eventsSince("p", 0))
                .extracting(event -> event.get("event_type"))
                .contains("outline.built");
    }

    @Test
    @DisplayName("confirm 重建发 path.rebuilt")
    void rebuildEmitsPathRebuilt() {
        service.build("p", payload(module("A", null, kp("alpha", "memory", null))), false);
        service.build("p", payload(module("B", null, kp("beta", "memory", null))), true);

        assertThat(store.eventsSince("p", 0))
                .extracting(event -> event.get("event_type"))
                .contains("path.rebuilt");
    }

    @Test
    @DisplayName("被拒绝的重建不产生事件")
    void refusedRebuildEmitsNothing() {
        service.build("p", payload(module("A", null, kp("alpha", "memory", null))), false);
        int before = store.eventsSince("p", 0).size();

        service.build("p", payload(module("B", null, kp("beta", "memory", null))), false);

        assertThat(store.eventsSince("p", 0)).hasSize(before);
    }

    @Test
    @DisplayName("revise 发 outline.revised")
    void reviseEmitsEvent() {
        service.build("p", payload(module("A", null, kp("alpha", "memory", null))), false);

        Map<String, Object> op = new LinkedHashMap<>();
        op.put("op", "add_kp");
        op.put("module_id", "m1");
        op.put("name", "new point");
        op.put("type", "concept");
        ReviseResult result = service.revise("p", List.of(op));

        assertThat(result.status()).isEqualTo(ReviseResult.REVISED);
        assertThat(store.eventsSince("p", 0))
                .extracting(event -> event.get("event_type"))
                .contains("outline.revised");
    }

    @Test
    @DisplayName("getCoverage 返回持久化报告")
    void getCoverageReturnsReport() {
        service.attachSources("p", List.of(source("s1", "a.md")));
        service.build("p", payload(module("A", null, kp("alpha", "concept", List.of("a.md")))), false);

        CoverageReport report = service.getCoverage("p");

        assertThat(report).isNotNull();
        assertThat(report.getDocuments()).isEqualTo(1);
        assertThat(report.getCovered()).isEqualTo(1);
    }

    @Test
    @DisplayName("getCoverage 对未知路径返回 null")
    void getCoverageMissingPath() {
        assertThat(service.getCoverage("nope")).isNull();
    }

    @Test
    @DisplayName("build 保留路径 goal（TC-C17 保护）")
    void buildKeepsGoal() {
        service.createPath("p", "learn Rust", "P");

        service.build("p", payload(module("A", null, kp("alpha", "memory", null))), false);

        assertThat(store.load("p").getGoal()).isEqualTo("learn Rust");
    }

    @Test
    @DisplayName("重建也保留路径 goal（TC-C17）")
    void rebuildKeepsGoal() {
        service.createPath("p", "learn Rust", "P");
        service.build("p", payload(module("A", null, kp("alpha", "memory", null))), false);

        service.build("p", payload(module("B", null, kp("beta", "memory", null))), true);

        assertThat(store.load("p").getGoal()).isEqualTo("learn Rust");
    }
}
