package com.masterypath.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.masterypath.domain.model.LearningProgress;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * DDL 与数据兼容：Java 版必须能直接打开参考实现（Python）建出的库文件。
 *
 * <p>fixture {@code fixtures/reference-workspace/learning.db} 由
 * {@code ../MasteryPath} 的 {@code LearningStore} 真实创建（含一次 mutate + 一条事件），
 * 未经任何手工修改。这条用例是「数据等价」维度里关于<b>表结构</b>的实证：
 * 只要 DDL 有一个列名或类型不一致，这里就会失败。
 */
class ReferenceWorkspaceCompatibilityTest {

    @TempDir
    Path tempDir;

    private Path copyFixture() throws Exception {
        Path source = fixturePath();
        Path target = tempDir.resolve("learning.db");
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return tempDir;
    }

    private Path fixturePath() throws URISyntaxException {
        var url = getClass().getResource("/fixtures/reference-workspace/learning.db");
        assertThat(url).as("缺少 fixture: fixtures/reference-workspace/learning.db").isNotNull();
        return Path.of(url.toURI());
    }

    /** 打开旧库 → 读到参考实现写入的路径聚合。 */
    @Test
    void readsPathWrittenByReferenceImplementation() throws Exception {
        try (LearningStore store = new LearningStore(copyFixture())) {
            LearningProgress progress = store.load("ref");

            assertThat(progress).isNotNull();
            assertThat(progress.getName()).isEqualTo("Ref");
            assertThat(progress.getGoal()).isEqualTo("G");
            assertThat(progress.getVersion()).isEqualTo(2);
            assertThat(progress.getModules()).hasSize(1);
            assertThat(progress.getModules().get(0).getKnowledgePoints().get(0).getName()).isEqualTo("ab");
            assertThat(progress.getExplainedObjectives()).containsKey("k1");
        }
    }

    /** 打开旧库 → 读到参考实现写入的事件。 */
    @Test
    void readsEventsWrittenByReferenceImplementation() throws Exception {
        try (LearningStore store = new LearningStore(copyFixture())) {
            List<Map<String, Object>> events = store.eventsSince("ref", 0);

            assertThat(events).hasSize(1);
            assertThat(events.get(0).get("event_type")).isEqualTo("objective.explained");
            assertThat(events.get(0).get("revision")).isEqualTo(2);
            assertThat(String.valueOf(events.get(0).get("payload"))).contains("k1");
        }
    }

    /** 打开旧库 → 继续 mutate，版本从旧值继续递增（证明 CAS 列语义一致）。 */
    @Test
    void continuesMutatingReferenceDatabase() throws Exception {
        try (LearningStore store = new LearningStore(copyFixture())) {
            store.mutate("ref", tx -> {
                tx.progress().getExplainedObjectives().put("k2", Map.of("summary", "y"));
                tx.emit("objective.explained", Map.of("knowledge_point_id", "k2"));
                return null;
            });

            LearningProgress progress = store.load("ref");
            assertThat(progress.getVersion()).isEqualTo(3);
            assertThat(progress.getExplainedObjectives()).containsKeys("k1", "k2");
            assertThat(store.eventsSince("ref", 2)).hasSize(1);
        }
    }

    /** 表结构逐项比对：列名必须与参考实现一致。 */
    @Test
    void tableColumnsMatchReference() throws Exception {
        try (LearningStore store = new LearningStore(copyFixture())) {
            assertThat(columnsOf(store, "paths"))
                    .containsExactly("path_id", "version", "data", "updated_at");
            assertThat(columnsOf(store, "events"))
                    .containsExactly("id", "path_id", "revision", "event_type", "payload",
                            "session_id", "turn_id", "created_at");
            assertThat(columnsOf(store, "interactions"))
                    .containsExactly("interaction_id", "path_id", "status", "data",
                            "created_at", "updated_at");
            assertThat(columnsOf(store, "question_bank"))
                    .containsExactly("entry_id", "path_id", "data", "created_at", "updated_at");
            assertThat(columnsOf(store, "sessions"))
                    .containsExactly("session_id", "path_id", "title", "mode", "status",
                            "message_count", "last_message", "has_pending_question",
                            "preferences", "created_at", "updated_at");
            assertThat(columnsOf(store, "messages"))
                    .containsExactly("id", "session_id", "turn_id", "role", "content", "data", "created_at");
        }
    }

    private List<String> columnsOf(LearningStore store, String table) {
        return store.tableColumns(table);
    }
}
