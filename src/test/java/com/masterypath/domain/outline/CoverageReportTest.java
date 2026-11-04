package com.masterypath.domain.outline;

import com.masterypath.domain.model.CoverageReport;
import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningModule;
import com.masterypath.domain.model.LearningProgress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖报告（{@code TC-N1} / {@code TC-N2} / {@code TC-N3} / {@code TC-N4}）。
 *
 * <p>核心：{@code reported=false} 时 {@code missing} MUST 为空；无知识点时接地比为 1.0。
 */
class CoverageReportTest {

    private static LearningProgress progress(KnowledgePoint... points) {
        LearningProgress progress = new LearningProgress("p");
        LearningModule module = new LearningModule("m1", "M", 0);
        module.setKnowledgePoints(List.of(points));
        progress.setModules(List.of(module));
        return progress;
    }

    private static KnowledgePoint kp(String id, String name, List<String> sources) {
        KnowledgePoint point = new KnowledgePoint(id, name, KnowledgeType.CONCEPT, "m1");
        point.setSourceIds(sources);
        return point;
    }

    @Test
    @DisplayName("TC-N1：未声明材料时 documents=0、reported=false、acyclic=true")
    void unreportedCoverage() {
        LearningProgress progress = progress(kp("k1", "obj", List.of()));

        CoverageReport report = OutlineBuilder.computeCoverage(progress, Set.of());

        assertThat(report.getDocuments()).isZero();
        assertThat(report.isReported()).isFalse();
        assertThat(report.isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("TC-N2：未被使用的来源进入 missing，covered 只计命中项")
    void unusedSourceIsMissing() {
        LearningProgress progress = progress(kp("k1", "obj", List.of("s1")));

        CoverageReport report = OutlineBuilder.computeCoverage(progress, Set.of("s1", "s2"));

        assertThat(report.getDocuments()).isEqualTo(2);
        assertThat(report.getCovered()).isEqualTo(1);
        assertThat(report.getMissing()).hasSize(1);
        assertThat(report.getMissing().get(0).getLabel()).isEqualTo("s2");
    }

    @Test
    @DisplayName("TC-N3：未声明材料时 missing 不展开")
    void unreportedDoesNotExpandMissing() {
        LearningProgress progress = progress(kp("k1", "obj", List.of()));

        CoverageReport report = OutlineBuilder.computeCoverage(progress, Set.of("s1", "s2"));

        assertThat(report.isReported()).isFalse();
        assertThat(report.getMissing()).isEmpty();
        assertThat(report.getDocuments()).isEqualTo(2);
        assertThat(report.getCovered()).isZero();
    }

    @Test
    @DisplayName("TC-N4：环形前置被标记为有环")
    void cycleDetected() {
        KnowledgePoint alpha = kp("k1", "alpha", List.of());
        alpha.setPrerequisiteIds(List.of("k2"));
        KnowledgePoint beta = kp("k2", "beta", List.of());
        beta.setPrerequisiteIds(List.of("k1"));

        CoverageReport report = OutlineBuilder.computeCoverage(progress(alpha, beta), Set.of());

        assertThat(report.isAcyclic()).isFalse();
    }

    @Test
    @DisplayName("TC-N4：无环前置为 acyclic=true")
    void acyclicWhenNoCycle() {
        KnowledgePoint alpha = kp("k1", "alpha", List.of());
        KnowledgePoint beta = kp("k2", "beta", List.of());
        beta.setPrerequisiteIds(List.of("k1"));

        CoverageReport report = OutlineBuilder.computeCoverage(progress(alpha, beta), Set.of());

        assertThat(report.isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("TC-N4：指向不存在节点的前置不算环")
    void danglingPrerequisiteIsNotCycle() {
        KnowledgePoint alpha = kp("k1", "alpha", List.of());
        alpha.setPrerequisiteIds(List.of("ghost"));

        CoverageReport report = OutlineBuilder.computeCoverage(progress(alpha), Set.of());

        assertThat(report.isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("无知识点时接地比为 1.0（不是 0）")
    void emptyPathGroundingIsOne() {
        LearningProgress progress = progress();

        CoverageReport report = OutlineBuilder.computeCoverage(progress, Set.of());

        assertThat(report.getGroundingRatio()).isEqualTo(1.0);
        assertThat(report.isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("接地比 = 有来源的知识点数 / 总数")
    void groundingRatioCountsGrounded() {
        LearningProgress progress = progress(
                kp("k1", "alpha", List.of("s1")),
                kp("k2", "beta", List.of()));

        CoverageReport report = OutlineBuilder.computeCoverage(progress, Set.of("s1"));

        assertThat(report.getGroundingRatio()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("全部来源被使用 → missing 为空、covered 等于 documents")
    void fullyCovered() {
        LearningProgress progress = progress(kp("k1", "alpha", List.of("s1", "s2")));

        CoverageReport report = OutlineBuilder.computeCoverage(progress, Set.of("s1", "s2"));

        assertThat(report.getMissing()).isEmpty();
        assertThat(report.getCovered()).isEqualTo(2);
    }

    @Test
    @DisplayName("missing 按来源 id 升序")
    void missingIsSorted() {
        LearningProgress progress = progress(kp("k1", "alpha", List.of("s1")));

        CoverageReport report = OutlineBuilder.computeCoverage(
                progress, Set.of("s1", "s9", "s3", "s5"));

        assertThat(report.getMissing())
                .extracting(com.masterypath.domain.model.CoverageGap::getLabel)
                .containsExactly("s3", "s5", "s9");
    }

    @Test
    @DisplayName("self-loop 也算环")
    void selfLoopIsCycle() {
        KnowledgePoint alpha = kp("k1", "alpha", List.of());
        alpha.setPrerequisiteIds(List.of("k1"));

        CoverageReport report = OutlineBuilder.computeCoverage(progress(alpha), Set.of());

        assertThat(report.isAcyclic()).isFalse();
    }

    @Test
    @DisplayName("三节点长环被检出")
    void threeNodeCycleDetected() {
        KnowledgePoint a = kp("k1", "alpha", List.of());
        a.setPrerequisiteIds(List.of("k2"));
        KnowledgePoint b = kp("k2", "beta", List.of());
        b.setPrerequisiteIds(List.of("k3"));
        KnowledgePoint c = kp("k3", "gamma", List.of());
        c.setPrerequisiteIds(List.of("k1"));

        CoverageReport report = OutlineBuilder.computeCoverage(progress(a, b, c), Set.of());

        assertThat(report.isAcyclic()).isFalse();
    }
}
