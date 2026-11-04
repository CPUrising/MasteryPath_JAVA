package com.masterypath.domain.outline;

import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.LearningModule;
import com.masterypath.domain.model.LearningProgress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * build 语义（{@code TC-J1} / {@code TC-J2} / {@code TC-J3} / {@code TC-J6}）。
 *
 * <p>核心：非空路径无 {@code confirm} 时 MUST 返回 {@code refused_rebuild} 且零状态变更；
 * 但 payload 非法时 MUST 抛错而不是返回拒绝（顺序敏感）。
 */
class OutlineBuildTest {

    private static Map<String, Object> module(String name, Map<String, Object>... kps) {
        Map<String, Object> module = new LinkedHashMap<>();
        module.put("name", name);
        module.put("knowledge_points", List.of(kps));
        return module;
    }

    private static Map<String, Object> kp(String name, String type) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("name", name);
        point.put("type", type);
        return point;
    }

    @SafeVarargs
    private static Map<String, Object> payload(Map<String, Object>... modules) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("modules", List.of(modules));
        return payload;
    }

    private static LearningProgress empty() {
        return new LearningProgress("p");
    }

    // -- TC-J1 ----------------------------------------------------------------

    @Test
    @DisplayName("TC-J1：空路径 build 出与入参一致的模块与知识点")
    void buildOnEmptyPath() {
        LearningProgress progress = empty();

        BuildResult result = OutlineBuilder.build(progress,
                payload(module("Basics", kp("objective one", "concept"), kp("objective two", "memory"))),
                false, null, Set.of());

        assertThat(result.status()).isEqualTo(BuildResult.BUILT);
        assertThat(result.modules()).hasSize(1);
        assertThat(result.modules().get(0).getKnowledgePoints()).hasSize(2);
        assertThat(result.rebuilt()).isFalse();
        assertThat(progress.getModules()).hasSize(1);
    }

    @Test
    @DisplayName("TC-J1：id 按 m1/k1 递增分配，且写入 knowledgeTypes")
    void buildAssignsIdsAndTypes() {
        LearningProgress progress = empty();

        OutlineBuilder.build(progress,
                payload(module("A", kp("first", "concept"), kp("second", "memory"))),
                false, null, Set.of());

        LearningModule module = progress.getModules().get(0);
        assertThat(module.getId()).isEqualTo("m1");
        assertThat(module.getKnowledgePoints())
                .extracting(KnowledgePoint::getId)
                .containsExactly("k1", "k2");
        assertThat(progress.getKnowledgeTypes())
                .containsEntry("k1", com.masterypath.domain.model.KnowledgeType.CONCEPT)
                .containsEntry("k2", com.masterypath.domain.model.KnowledgeType.MEMORY);
    }

    @Test
    @DisplayName("TC-J1：currentModuleId 设为第一个模块")
    void buildSetsCurrentModule() {
        LearningProgress progress = empty();

        OutlineBuilder.build(progress, payload(module("A", kp("first", "memory"))),
                false, null, Set.of());

        assertThat(progress.getCurrentModuleId()).isEqualTo("m1");
    }

    @Test
    @DisplayName("模块 order 按 payload 顺序 0..n")
    void buildSetsModuleOrder() {
        LearningProgress progress = empty();

        OutlineBuilder.build(progress,
                payload(module("A", kp("first", "memory")), module("B", kp("second", "memory"))),
                false, null, Set.of());

        assertThat(progress.getModules())
                .extracting(LearningModule::getOrder)
                .containsExactly(0, 1);
    }

    // -- TC-J2 ----------------------------------------------------------------

    @Test
    @DisplayName("TC-J2：非空路径无 confirm 返回 refused_rebuild 并列出将丢失的 id")
    void rebuildWithoutConfirmRefused() {
        LearningProgress progress = empty();
        OutlineBuilder.build(progress, payload(module("A", kp("one", "memory"))),
                false, null, Set.of());

        BuildResult result = OutlineBuilder.build(progress,
                payload(module("B", kp("two", "memory"))), false, null, Set.of());

        assertThat(result.status()).isEqualTo(BuildResult.REFUSED_REBUILD);
        assertThat(result.dropped()).containsExactly("k1");
        assertThat(result.message()).contains("confirm=true");
    }

    @Test
    @DisplayName("TC-J2：被拒绝时地图与进度零变更")
    void refusedRebuildLeavesStateUntouched() {
        LearningProgress progress = empty();
        OutlineBuilder.build(progress, payload(module("A", kp("one", "memory"))),
                false, null, Set.of());
        progress.getMasteryLevels().put("k1", 1.0);

        OutlineBuilder.build(progress, payload(module("B", kp("two", "memory"))),
                false, null, Set.of());

        assertThat(progress.getModules()).hasSize(1);
        assertThat(progress.getModules().get(0).getName()).isEqualTo("A");
        assertThat(progress.getMasteryLevels()).containsEntry("k1", 1.0);
    }

    @Test
    @DisplayName("TC-J2：confirm=true 时替换并清空学习态")
    void rebuildWithConfirmReplacesAndResets() {
        LearningProgress progress = empty();
        OutlineBuilder.build(progress, payload(module("A", kp("one", "memory"))),
                false, null, Set.of());
        progress.getMasteryLevels().put("k1", 1.0);
        progress.getDeferredObjectives().put("k1",
                new com.masterypath.domain.model.DeferredObjective("k1", "later"));

        BuildResult result = OutlineBuilder.build(progress,
                payload(module("B", kp("two", "memory"))), true, null, Set.of());

        assertThat(result.status()).isEqualTo(BuildResult.BUILT);
        assertThat(result.rebuilt()).isTrue();
        assertThat(progress.getModules().get(0).getName()).isEqualTo("B");
        assertThat(progress.getModules().get(0).getKnowledgePoints())
                .extracting(KnowledgePoint::getId)
                .containsExactly("k1");
        assertThat(progress.getMasteryLevels()).isEmpty();
        assertThat(progress.getDeferredObjectives()).isEmpty();
    }

    @Test
    @DisplayName("顺序敏感：非空路径 + 非法 payload + 无 confirm → 抛错而非 refused_rebuild")
    void invalidPayloadThrowsBeforeConfirmCheck() {
        LearningProgress progress = empty();
        OutlineBuilder.build(progress, payload(module("A", kp("one", "memory"))),
                false, null, Set.of());

        assertThatThrownBy(() -> OutlineBuilder.build(progress,
                Map.of("modules", List.of()), false, null, Set.of()))
                .isInstanceOf(OutlineException.class)
                .hasMessageContaining("non-empty 'modules' array");
    }

    // -- TC-J3 ----------------------------------------------------------------

    @Test
    @DisplayName("TC-J3：同名知识点被拒绝")
    void duplicateNameRejected() {
        assertThatThrownBy(() -> OutlineBuilder.build(empty(),
                payload(module("A", kp("same name", "memory"), kp("same name", "concept"))),
                false, null, Set.of()))
                .isInstanceOf(OutlineException.class)
                .hasMessageContaining("duplicate knowledge point name");
    }

    @Test
    @DisplayName("跨模块同名也被拒绝")
    void duplicateNameAcrossModulesRejected() {
        assertThatThrownBy(() -> OutlineBuilder.build(empty(),
                payload(module("A", kp("same name", "memory")),
                        module("B", kp("same name", "memory"))),
                false, null, Set.of()))
                .isInstanceOf(OutlineException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    @DisplayName("名字不足 2 字符被拒绝")
    void shortNameRejected() {
        assertThatThrownBy(() -> OutlineBuilder.build(empty(),
                payload(module("A", kp("x", "memory"))), false, null, Set.of()))
                .isInstanceOf(OutlineException.class)
                .hasMessageContaining("at least 2 characters");
    }

    @Test
    @DisplayName("未知类型被拒绝")
    void unknownTypeRejected() {
        assertThatThrownBy(() -> OutlineBuilder.build(empty(),
                payload(module("A", kp("valid name", "foo"))), false, null, Set.of()))
                .isInstanceOf(OutlineException.class)
                .hasMessageContaining("invalid type");
    }

    @Test
    @DisplayName("modules 缺失或为空数组被拒绝")
    void missingModulesRejected() {
        assertThatThrownBy(() -> OutlineBuilder.build(empty(), Map.of(), false, null, Set.of()))
                .isInstanceOf(OutlineException.class);
        assertThatThrownBy(() -> OutlineBuilder.build(empty(),
                Map.of("modules", List.of()), false, null, Set.of()))
                .isInstanceOf(OutlineException.class);
    }

    @Test
    @DisplayName("模块不是对象被拒绝")
    void nonObjectModuleRejected() {
        assertThatThrownBy(() -> OutlineBuilder.build(empty(),
                Map.of("modules", List.of("not a module")), false, null, Set.of()))
                .isInstanceOf(OutlineException.class)
                .hasMessageContaining("each module must be an object");
    }

    // -- 前置解析 -------------------------------------------------------------

    @Test
    @DisplayName("前置按名字解析为 id，支持前向引用")
    void prerequisitesResolveByNameWithForwardReference() {
        LearningProgress progress = empty();
        Map<String, Object> first = kp("alpha", "memory");
        first.put("prerequisites", List.of("beta"));
        Map<String, Object> second = kp("beta", "memory");

        OutlineBuilder.build(progress, payload(module("A", first, second)), false, null, Set.of());

        List<KnowledgePoint> points = progress.getModules().get(0).getKnowledgePoints();
        assertThat(points.get(0).getPrerequisiteIds()).containsExactly("k2");
    }

    @Test
    @DisplayName("未知前置被拒绝")
    void unknownPrerequisiteRejected() {
        Map<String, Object> first = kp("alpha", "memory");
        first.put("prerequisites", List.of("nope"));

        assertThatThrownBy(() -> OutlineBuilder.build(empty(),
                payload(module("A", first)), false, null, Set.of()))
                .isInstanceOf(OutlineException.class)
                .hasMessageContaining("unknown prerequisite");
    }

    // -- 来源接地 -------------------------------------------------------------

    @Test
    @DisplayName("模块 materials 只补给未声明来源的知识点")
    void materialsGroundOnlyUngroundedPoints() {
        LearningProgress progress = empty();
        SourceResolver resolver = SourceResolver.of(
                Map.of("a.md", "s1"), Set.of("s1"));
        Map<String, Object> withSource = kp("alpha", "memory");
        withSource.put("sources", List.of("a.md"));
        Map<String, Object> withoutSource = kp("beta", "memory");

        Map<String, Object> moduleSpec = module("A", withSource, withoutSource);
        Map<String, Object> mutable = new LinkedHashMap<>(moduleSpec);
        mutable.put("materials", List.of("a.md"));

        OutlineBuilder.build(progress, payload(mutable), false, resolver, Set.of("s1"));

        List<KnowledgePoint> points = progress.getModules().get(0).getKnowledgePoints();
        assertThat(points.get(0).getSourceIds()).containsExactly("s1");
        assertThat(points.get(1).getSourceIds()).containsExactly("s1");
    }

    @Test
    @DisplayName("未命中的来源标签被丢弃")
    void unknownSourceLabelDropped() {
        LearningProgress progress = empty();
        SourceResolver resolver = SourceResolver.of(Map.of("a.md", "s1"), Set.of("s1"));
        Map<String, Object> point = kp("alpha", "memory");
        point.put("sources", List.of("a.md", "missing.md"));

        OutlineBuilder.build(progress, payload(module("A", point)), false, resolver, Set.of("s1"));

        assertThat(progress.getModules().get(0).getKnowledgePoints().get(0).getSourceIds())
                .containsExactly("s1");
    }

    // -- TC-J6 ----------------------------------------------------------------

    @Test
    @DisplayName("TC-J6：无来源的知识点产生 goal_gap")
    void buildReportsGaps() {
        LearningProgress progress = empty();

        BuildResult result = OutlineBuilder.build(progress,
                payload(module("A", kp("no source", "concept"))), false, null, Set.of());

        assertThat(result.gaps()).isNotEmpty();
        assertThat(result.gaps()).allMatch(
                gap -> gap.getKind() == com.masterypath.domain.model.GapKind.GOAL_GAP);
        assertThat(result.gaps().get(0).getId()).isEqualTo("g1");
    }

    @Test
    @DisplayName("缺口 id 在 build 时基于已有缺口数续号（无 KP 时不重置学习态）")
    void gapIdsContinueFromExisting() {
        LearningProgress progress = empty();
        // 只有空模块（无 KP）→ hasContent=false，不触发 resetLearningState，
        // 但 selected 中有未使用的来源 → 产生 source_omission 缺口 g1。
        OutlineBuilder.build(progress, payload(module("A")), false, null, Set.of("s1"));
        assertThat(progress.getSourceGaps()).hasSize(1);
        assertThat(progress.getSourceGaps().get(0).getId()).isEqualTo("g1");

        BuildResult second = OutlineBuilder.build(progress, payload(module("B")),
                false, null, Set.of("s1", "s2"));

        assertThat(second.gaps()).hasSize(2);
        assertThat(second.gaps().get(0).getId()).isEqualTo("g2");
    }

    @Test
    @DisplayName("confirm 重建会重置学习态，缺口 id 从 g1 重新开始")
    void rebuildResetsGapIds() {
        LearningProgress progress = empty();
        OutlineBuilder.build(progress, payload(module("A", kp("no source", "concept"))),
                false, null, Set.of());
        assertThat(progress.getSourceGaps().get(0).getId()).isEqualTo("g1");

        BuildResult rebuilt = OutlineBuilder.build(progress,
                payload(module("B", kp("still none", "concept"))), true, null, Set.of());

        assertThat(rebuilt.gaps().get(0).getId()).isEqualTo("g1");
    }

    @Test
    @DisplayName("build 后写入覆盖报告")
    void buildWritesCoverage() {
        LearningProgress progress = empty();

        OutlineBuilder.build(progress, payload(module("A", kp("alpha", "memory"))),
                false, null, Set.of());

        assertThat(progress.getCoverage()).isNotNull();
        assertThat(progress.getCoverage().isAcyclic()).isTrue();
    }
}
