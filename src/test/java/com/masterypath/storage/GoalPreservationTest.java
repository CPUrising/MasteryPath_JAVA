package com.masterypath.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.masterypath.domain.model.LearningModule;
import com.masterypath.domain.model.LearningProgress;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TC-C17：目标在写入后必须保留（{@code INV-13}）。
 *
 * <p>放在存储层是因为「保留」这件事由「读-改-写」的往返决定：
 * 只要序列化丢了 {@code goal}，这里就会失败。它同时守护 {@code 0002} 的 JSON 映射。
 */
class GoalPreservationTest {

    @TempDir
    Path tempDir;

    @Test
    void goalSurvivesMutation() {
        try (LearningStore store = new LearningStore(tempDir)) {
            LearningProgress progress = new LearningProgress("p1");
            progress.setGoal("master Rust concurrency");
            LearningModule module = new LearningModule("m1", "M1", 0);
            progress.setModules(List.of(module));
            store.create(progress);

            store.mutate("p1", tx -> {
                tx.progress().getExplainedObjectives().put("x", Map.of());
                return null;
            });

            assertThat(store.load("p1").getGoal()).isEqualTo("master Rust concurrency");
        }
    }

    /** 空目标也不会被意外写成别的值。 */
    @Test
    void emptyGoalStaysEmpty() {
        try (LearningStore store = new LearningStore(tempDir)) {
            store.create(new LearningProgress("p2"));
            store.mutate("p2", tx -> {
                tx.progress().setName("renamed");
                return null;
            });

            assertThat(store.load("p2").getGoal()).isEmpty();
        }
    }
}
