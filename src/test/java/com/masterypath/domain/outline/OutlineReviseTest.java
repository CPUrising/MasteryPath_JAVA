package com.masterypath.domain.outline;

import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningModule;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.LearningEvidence;
import com.masterypath.domain.model.QuizAttempt;
import com.masterypath.domain.model.ReviewTask;
import com.masterypath.domain.model.TimeSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * revise 语义（{@code TC-J4} / {@code TC-J5} + 8 个 op）。
 *
 * <p>核心：未触动知识点的进度 MUST 逐字段保留；类型 MUST 只读。
 */
class OutlineReviseTest {

    private static LearningProgress built(String... names) {
        LearningProgress progress = new LearningProgress("p");
        LearningModule module = new LearningModule("m1", "A", 0);
        int index = 0;
        for (String name : names) {
            index++;
            module.getKnowledgePoints().add(
                    new KnowledgePoint("k" + index, name, KnowledgeType.MEMORY, "m1"));
            progress.getKnowledgeTypes().put("k" + index, KnowledgeType.MEMORY);
        }
        progress.getModules().add(module);
        return progress;
    }

    private static Map<String, Object> op(String name) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("op", name);
        return op;
    }

    // -- TC-J4 ----------------------------------------------------------------

    @Test
    @DisplayName("TC-J4：add_kp 保留未触动知识点的掌握度")
    void addKpPreservesProgress() {
        LearningProgress progress = built("keep me", "also here");
        progress.getMasteryLevels().put("k1", 1.0);

        ReviseResult result = OutlineBuilder.revise(progress,
                List.of(with(op("add_kp"), "module_id", "m1", "name", "brand new",
                        "type", "procedure")),
                Set.of());

        assertThat(result.status()).isEqualTo(ReviseResult.REVISED);
        assertThat(progress.getMasteryLevels()).containsEntry("k1", 1.0);
        assertThat(progress.getModules().get(0).getKnowledgePoints()).hasSize(3);
        assertThat(result.applied()).contains("add_kp:k3");
    }

    @Test
    @DisplayName("TC-J4：add_kp 同时写入 knowledgeTypes")
    void addKpWritesType() {
        LearningProgress progress = built("one");

        OutlineBuilder.revise(progress,
                List.of(with(op("add_kp"), "module_id", "m1", "name", "new point",
                        "type", "concept")),
                Set.of());

        assertThat(progress.getKnowledgeTypes()).containsEntry("k2", KnowledgeType.CONCEPT);
    }

    @Test
    @DisplayName("TC-J4：update_kp 改名保留进度")
    void updateKpKeepsProgress() {
        LearningProgress progress = built("one");
        progress.getMasteryLevels().put("k1", 0.95);

        OutlineBuilder.revise(progress,
                List.of(with(op("update_kp"), "kp_id", "k1", "name", "renamed point")),
                Set.of());

        assertThat(progress.getModules().get(0).getKnowledgePoints().get(0).getName())
                .isEqualTo("renamed point");
        assertThat(progress.getMasteryLevels()).containsEntry("k1", 0.95);
    }

    // -- TC-J5 ----------------------------------------------------------------

    @Test
    @DisplayName("TC-J5：修改类型被拒绝，文案逐字")
    void typeChangeRefused() {
        LearningProgress progress = built("one");
        progress.getModules().get(0).getKnowledgePoints().get(0).setType(KnowledgeType.CONCEPT);

        assertThatThrownBy(() -> OutlineBuilder.revise(progress,
                List.of(with(op("update_kp"), "kp_id", "k1", "type", "memory")), Set.of()))
                .isInstanceOf(OutlineException.class)
                .hasMessage("knowledge point type is read-only (ADR-003); remove and re-add to change it");
    }

    @Test
    @DisplayName("TC-J5：声明相同类型不报错")
    void sameTypeAllowed() {
        LearningProgress progress = built("one");

        ReviseResult result = OutlineBuilder.revise(progress,
                List.of(with(op("update_kp"), "kp_id", "k1", "type", "memory")), Set.of());

        assertThat(result.applied()).contains("update_kp:k1");
    }

    // -- 8 个 op --------------------------------------------------------------

    @Test
    @DisplayName("add_module：分配下一个 m id 并追加")
    void addModuleOp() {
        LearningProgress progress = built("one");

        ReviseResult result = OutlineBuilder.revise(progress,
                List.of(with(op("add_module"), "name", "Second")), Set.of());

        assertThat(progress.getModules()).hasSize(2);
        assertThat(progress.getModules().get(1).getId()).isEqualTo("m2");
        assertThat(result.applied()).contains("add_module:m2");
    }

    @Test
    @DisplayName("update_module：改名与 objective")
    void updateModuleOp() {
        LearningProgress progress = built("one");

        OutlineBuilder.revise(progress,
                List.of(with(op("update_module"), "module_id", "m1",
                        "name", "Renamed", "objective", "do things")),
                Set.of());

        LearningModule module = progress.getModules().get(0);
        assertThat(module.getName()).isEqualTo("Renamed");
        assertThat(module.getObjective()).isEqualTo("do things");
    }

    @Test
    @DisplayName("remove_module：连带清除其知识点的全部学习态")
    void removeModuleOp() {
        LearningProgress progress = built("one", "two");
        progress.getMasteryLevels().put("k1", 1.0);
        progress.getMasteryLevels().put("k2", 0.5);

        ReviseResult result = OutlineBuilder.revise(progress,
                List.of(with(op("remove_module"), "module_id", "m1")), Set.of());

        assertThat(progress.getModules()).isEmpty();
        assertThat(progress.getMasteryLevels()).isEmpty();
        assertThat(result.droppedKpIds()).containsExactly("k1", "k2");
    }

    @Test
    @DisplayName("remove_kp：清除该知识点的全部学习态")
    void removeKpOp() {
        LearningProgress progress = built("one", "two");
        progress.getMasteryLevels().put("k1", 1.0);
        progress.getMasteryLevels().put("k2", 0.5);

        ReviseResult result = OutlineBuilder.revise(progress,
                List.of(with(op("remove_kp"), "kp_id", "k1")), Set.of());

        assertThat(progress.getModules().get(0).getKnowledgePoints())
                .extracting(KnowledgePoint::getId)
                .containsExactly("k2");
        assertThat(progress.getMasteryLevels()).containsOnlyKeys("k2");
        assertThat(result.droppedKpIds()).containsExactly("k1");
    }

    @Test
    @DisplayName("move_kp：跨模块移动并更新 moduleId")
    void moveKpOp() {
        LearningProgress progress = built("one");
        progress.getModules().add(new LearningModule("m2", "B", 1));

        OutlineBuilder.revise(progress,
                List.of(with(op("move_kp"), "kp_id", "k1", "module_id", "m2")), Set.of());

        assertThat(progress.getModules().get(0).getKnowledgePoints()).isEmpty();
        assertThat(progress.getModules().get(1).getKnowledgePoints()).hasSize(1);
        assertThat(progress.getModules().get(1).getKnowledgePoints().get(0).getModuleId())
                .isEqualTo("m2");
    }

    @Test
    @DisplayName("reorder：按给定顺序重排")
    void reorderOp() {
        LearningProgress progress = built("one", "two", "three");

        OutlineBuilder.revise(progress,
                List.of(with(op("reorder"), "module_id", "m1",
                        "kp_ids", List.of("k3", "k1", "k2"))),
                Set.of());

        assertThat(progress.getModules().get(0).getKnowledgePoints())
                .extracting(KnowledgePoint::getId)
                .containsExactly("k3", "k1", "k2");
    }

    @Test
    @DisplayName("reorder：未列全被拒绝")
    void reorderIncompleteRejected() {
        LearningProgress progress = built("one", "two");

        assertThatThrownBy(() -> OutlineBuilder.revise(progress,
                List.of(with(op("reorder"), "module_id", "m1", "kp_ids", List.of("k1"))),
                Set.of()))
                .isInstanceOf(OutlineException.class)
                .hasMessageContaining("every knowledge point id exactly once");
    }

    @Test
    @DisplayName("未知 op 被拒绝")
    void unknownOpRejected() {
        assertThatThrownBy(() -> OutlineBuilder.revise(built("one"),
                List.of(op("explode")), Set.of()))
                .isInstanceOf(OutlineException.class)
                .hasMessageContaining("unknown revise op");
    }

    @Test
    @DisplayName("ops 为 null 被拒绝")
    void nullOpsRejected() {
        assertThatThrownBy(() -> OutlineBuilder.revise(built("one"), null, Set.of()))
                .isInstanceOf(OutlineException.class)
                .hasMessageContaining("'ops' array");
    }

    @Test
    @DisplayName("未知 module_id 被拒绝")
    void unknownModuleRejected() {
        assertThatThrownBy(() -> OutlineBuilder.revise(built("one"),
                List.of(with(op("update_module"), "module_id", "nope")), Set.of()))
                .isInstanceOf(OutlineException.class)
                .hasMessageContaining("unknown module_id");
    }

    @Test
    @DisplayName("未知 kp_id 被拒绝")
    void unknownKpRejected() {
        assertThatThrownBy(() -> OutlineBuilder.revise(built("one"),
                List.of(with(op("remove_kp"), "kp_id", "nope")), Set.of()))
                .isInstanceOf(OutlineException.class)
                .hasMessageContaining("unknown knowledge_point_id");
    }

    // -- dropKpState 全字段 ---------------------------------------------------

    @Test
    @DisplayName("dropKpState 清空 7 个 map 与 3 个 list 的对应项")
    void dropKpStateClearsEverything() {
        LearningProgress progress = built("one", "two");
        progress.getMasteryLevels().put("k1", 1.0);
        progress.getQualitativeMastery().put("k1", true);
        progress.getRepetitionStates().put("k1",
                new com.masterypath.domain.model.RepetitionState());
        progress.getLearnerMasteryOverrides().put("k1",
                new com.masterypath.domain.model.LearnerMasteryOverride("k1", "note"));
        progress.getDeferredObjectives().put("k1",
                new com.masterypath.domain.model.DeferredObjective("k1", ""));
        progress.getExplainedObjectives().put("k1", Map.of("summary", "x"));

        QuizAttempt attempt = new QuizAttempt();
        attempt.setKnowledgePointId("k1");
        progress.getQuizAttempts().add(attempt);

        LearningEvidence evidence = new LearningEvidence();
        evidence.setKnowledgePointId("k1");
        progress.getLearningEvidence().add(evidence);

        ReviewTask task = new ReviewTask();
        task.setKnowledgePointId("k1");
        progress.getReviewQueue().add(task);

        OutlineBuilder.revise(progress, List.of(with(op("remove_kp"), "kp_id", "k1")), Set.of());

        assertThat(progress.getMasteryLevels()).doesNotContainKey("k1");
        assertThat(progress.getQualitativeMastery()).doesNotContainKey("k1");
        assertThat(progress.getKnowledgeTypes()).doesNotContainKey("k1");
        assertThat(progress.getRepetitionStates()).doesNotContainKey("k1");
        assertThat(progress.getLearnerMasteryOverrides()).doesNotContainKey("k1");
        assertThat(progress.getDeferredObjectives()).doesNotContainKey("k1");
        assertThat(progress.getExplainedObjectives()).doesNotContainKey("k1");
        assertThat(progress.getQuizAttempts()).isEmpty();
        assertThat(progress.getLearningEvidence()).isEmpty();
        assertThat(progress.getReviewQueue()).isEmpty();
    }

    // -- 缺口重算 -------------------------------------------------------------

    @Test
    @DisplayName("revise 的缺口 id 从 g1 重算（不续号）")
    void reviseGapIdsRestartFromOne() {
        LearningProgress progress = built("no source");

        ReviseResult result = OutlineBuilder.revise(progress, List.of(), Set.of());

        assertThat(result.gaps()).isNotEmpty();
        assertThat(result.gaps().get(0).getId()).isEqualTo("g1");
    }

    @Test
    @DisplayName("revise 后重算覆盖报告")
    void reviseRecomputesCoverage() {
        LearningProgress progress = built("one");

        OutlineBuilder.revise(progress, List.of(), Set.of());

        assertThat(progress.getCoverage()).isNotNull();
    }

    @Test
    @DisplayName("空 ops 列表合法，只重算缺口与覆盖")
    void emptyOpsAllowed() {
        ReviseResult result = OutlineBuilder.revise(built("one"), List.of(), Set.of());

        assertThat(result.applied()).isEmpty();
        assertThat(result.status()).isEqualTo(ReviseResult.REVISED);
    }

    private static Map<String, Object> with(Map<String, Object> base, Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>(base);
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }
}
