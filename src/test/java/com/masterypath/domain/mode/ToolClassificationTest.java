package com.masterypath.domain.mode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 分类表与 fail-closed 守卫（{@code TC-D9} / {@code INV-17}）。
 */
class ToolClassificationTest {

    @Test
    @DisplayName("TC-D9：默认清单完整归类，守卫不抛")
    void defaultClassificationIsComplete() {
        ToolClassification.assertClassificationComplete();
    }

    @Test
    @DisplayName("TC-D9：混入未归类工具即 fail-closed")
    void strayToolIsRejected() {
        Set<String> names = new LinkedHashSet<>(ToolClassification.allMasteryTools());
        names.add("mystery_tool");

        assertThatThrownBy(() -> ToolClassification.assertClassificationComplete(names))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fail-closed")
                .hasMessageContaining("mystery_tool");
    }

    @Test
    @DisplayName("共享集合与专属集合不相交")
    void sharedAndExclusiveDoNotOverlap() {
        Set<String> overlap = new LinkedHashSet<>(ToolClassification.SHARED_TOOLS);
        overlap.retainAll(ToolClassification.TOOL_MODES.keySet());

        assertThat(overlap).isEmpty();
    }

    @Test
    @DisplayName("每个 mastery_ 共享工具都在 SHARED_TOOLS 里，且不重复出现在 TOOL_MODES")
    void sharedMasteryToolsAreExplicit() {
        assertThat(ToolClassification.SHARED_TOOLS)
                .contains("mastery_status", "mastery_mode", "mastery_profile",
                        "mastery_paths", "mastery_switch", "mastery_leave");
        for (String tool : ToolClassification.SHARED_TOOLS) {
            assertThat(ToolClassification.TOOL_MODES).doesNotContainKey(tool);
        }
    }

    @Test
    @DisplayName("outline 专属只有 build 与 revise")
    void outlineOnlySetIsExact() {
        assertThat(ToolClassification.OUTLINE_ONLY)
                .containsExactlyInAnyOrder("mastery_build", "mastery_revise");
        for (String tool : ToolClassification.OUTLINE_ONLY) {
            assertThat(ToolClassification.TOOL_MODES.get(tool))
                    .containsExactly(SessionMode.OUTLINE);
        }
    }

    @Test
    @DisplayName("study/review 共用共 9 个工具，映射为 {STUDY, REVIEW}")
    void studyReviewSetIsExact() {
        assertThat(ToolClassification.STUDY_REVIEW).hasSize(9);
        assertThat(ToolClassification.STUDY_REVIEW)
                .containsExactlyInAnyOrder(
                        "mastery_quiz",
                        "mastery_grade",
                        "mastery_repair_question",
                        "mastery_assess",
                        "mastery_skip_question",
                        "mastery_defer_objective",
                        "mastery_note_explained",
                        "mastery_propose_objective",
                        "mastery_note_gap");
        for (String tool : ToolClassification.STUDY_REVIEW) {
            assertThat(ToolClassification.TOOL_MODES.get(tool))
                    .containsExactlyInAnyOrder(SessionMode.STUDY, SessionMode.REVIEW);
        }
    }

    @Test
    @DisplayName("allMasteryTools 排除非 mastery 命名空间的共享工具")
    void allMasteryToolsExcludesRagAndReadSource() {
        Set<String> names = ToolClassification.allMasteryTools();

        assertThat(names).doesNotContain("rag", "read_source");
        assertThat(names).hasSize(
                ToolClassification.TOOL_MODES.size()
                        + (int) ToolClassification.SHARED_TOOLS.stream()
                        .filter(name -> name.startsWith("mastery_"))
                        .count());
        assertThat(names).contains("mastery_status", "mastery_build", "mastery_quiz");
    }

    @Test
    @DisplayName("分类表不可变：外部修改尝试失败")
    void classificationTablesAreImmutable() {
        assertThatThrownBy(() -> ToolClassification.SHARED_TOOLS.add("mastery_hack"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ToolClassification.TOOL_MODES.put("mastery_hack", Set.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ToolClassification.OUTLINE_ONLY.add("mastery_hack"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("传入空清单时守卫通过（无未归类项）")
    void emptyListPasses() {
        ToolClassification.assertClassificationComplete(Set.of());
    }
}
