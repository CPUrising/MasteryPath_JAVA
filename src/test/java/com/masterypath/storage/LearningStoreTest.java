package com.masterypath.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masterypath.domain.model.LearningProgress;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** TC-B1…B8、B14、B15、B16：存储、CAS、事件（spec 03 §B）。 */
class LearningStoreTest {

    @TempDir
    Path tempDir;

    private LearningStore store;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    /** TC-B1：读不存在的路径返回 null，且不创建记录。 */
    @Test
    void loadMissingReturnsNullAndCreatesNothing() {
        assertThat(store.load("ghost")).isNull();
        assertThat(store.listPaths()).isEmpty();
    }

    /** TC-B2：创建后能读回同样内容。 */
    @Test
    void createThenLoad() {
        LearningProgress progress = new LearningProgress("p");
        progress.setName("P");
        store.create(progress);

        LearningProgress loaded = store.load("p");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getName()).isEqualTo("P");
    }

    /** 重复创建同一路径 → 前置条件失败。 */
    @Test
    void createTwiceRejected() {
        store.create(new LearningProgress("p"));
        assertThatThrownBy(() -> store.create(new LearningProgress("p")))
                .isInstanceOf(PreconditionFailedException.class);
    }

    /** TC-B3：一次成功 mutate 使 version +1 且 updated_at 增大。 */
    @Test
    void mutateBumpsVersion() {
        LearningProgress created = store.create(new LearningProgress("p"));
        assertThat(created.getVersion()).isEqualTo(1);
        double before = created.getUpdatedAt();

        store.mutate("p", tx -> {
            tx.progress().getExplainedObjectives().put("k", Map.of());
            return null;
        });

        LearningProgress loaded = store.load("p");
        assertThat(loaded.getVersion()).isEqualTo(2);
        assertThat(loaded.getUpdatedAt()).isGreaterThan(before);
    }

    /** TC-B4：用旧版本提交被拒，且不覆盖他人已提交的内容。 */
    @Test
    void staleExpectedVersionRejected() {
        store.create(new LearningProgress("p"));
        store.mutate("p", tx -> null); // version -> 2

        assertThatThrownBy(() -> store.mutate("p", tx -> null, 1, false))
                .isInstanceOf(PreconditionFailedException.class);
        assertThat(store.load("p").getVersion()).isEqualTo(2);
    }

    /** TC-B5 + TC-B15：回调抛异常 → 状态与事件都不落库。 */
    @Test
    void callbackExceptionRollsBackStateAndEvents() {
        store.create(new LearningProgress("p"));

        assertThatThrownBy(() -> store.mutate("p", tx -> {
            tx.progress().getExplainedObjectives().put("k", Map.of());
            tx.emit("objective.explained", Map.of("knowledge_point_id", "k"));
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(store.load("p").getVersion()).isEqualTo(1);
        assertThat(store.eventsSince("p", 0)).isEmpty();
    }

    /** TC-B6 + TC-B17：不同 workspace 同名路径互不可见。 */
    @Test
    void workspaceIsolation() {
        try (LearningStore a = new LearningStore(tempDir.resolve("ws_a"));
             LearningStore b = new LearningStore(tempDir.resolve("ws_b"))) {
            LearningProgress pa = new LearningProgress("p");
            pa.setName("A");
            a.create(pa);

            LearningProgress pb = new LearningProgress("p");
            pb.setName("B");
            b.create(pb);

            assertThat(a.load("p").getName()).isEqualTo("A");
            assertThat(b.load("p").getName()).isEqualTo("B");
        }
    }

    /** TC-B7：事件带 path_id 与提交后的 revision。 */
    @Test
    void eventCarriesPathAndRevision() {
        store.create(new LearningProgress("p"));
        store.mutate("p", tx -> {
            tx.emit("objective.explained", Map.of("knowledge_point_id", "k"));
            return null;
        });

        List<Map<String, Object>> events = store.eventsSince("p", 0);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("path_id")).isEqualTo("p");
        assertThat(events.get(0).get("revision")).isEqualTo(2);
    }

    /** TC-B8：事件只追加，顺序稳定。 */
    @Test
    void eventsAppendOnly() {
        store.create(new LearningProgress("p"));
        store.mutate("p", tx -> {
            tx.emit("e1", Map.of());
            return null;
        });
        store.mutate("p", tx -> {
            tx.emit("e2", Map.of());
            return null;
        });

        List<String> types = store.eventsSince("p", 0).stream()
                .map(row -> String.valueOf(row.get("event_type")))
                .toList();
        assertThat(types).containsExactly("e1", "e2");
    }

    /** eventsSince 只返回指定 revision 之后的事件。 */
    @Test
    void eventsSinceSkipsOlderRevisions() {
        store.create(new LearningProgress("p"));
        store.mutate("p", tx -> {
            tx.emit("e1", Map.of());
            return null;
        });
        store.mutate("p", tx -> {
            tx.emit("e2", Map.of());
            return null;
        });

        List<Map<String, Object>> afterTwo = store.eventsSince("p", 2);
        assertThat(afterTwo).hasSize(1);
        assertThat(afterTwo.get(0).get("event_type")).isEqualTo("e2");
    }

    /** TC-B14：WAL 已启用。 */
    @Test
    void walEnabled() {
        assertThat(store.journalMode().toLowerCase()).isEqualTo("wal");
    }

    /** TC-B16：一回合顺序写两条路径，均成功。 */
    @Test
    void sequentialTwoPaths() {
        store.create(new LearningProgress("p1"));
        store.create(new LearningProgress("p2"));

        store.mutate("p1", tx -> {
            tx.emit("switch", Map.of());
            return null;
        });
        store.mutate("p2", tx -> {
            tx.emit("switch", Map.of());
            return null;
        });

        assertThat(store.load("p1").getVersion()).isEqualTo(2);
        assertThat(store.load("p2").getVersion()).isEqualTo(2);
    }

    /** mutate 对不存在的路径 → 前置条件失败（不创建）。 */
    @Test
    void mutateMissingPathRejected() {
        assertThatThrownBy(() -> store.mutate("ghost", tx -> null))
                .isInstanceOf(PreconditionFailedException.class);
        assertThat(store.load("ghost")).isNull();
    }

    /** createIfMissing 为真时以空聚合创建。 */
    @Test
    void mutateCreatesWhenAsked() {
        store.mutate("fresh", tx -> {
            tx.progress().setName("F");
            return null;
        }, null, true);

        LearningProgress loaded = store.load("fresh");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getName()).isEqualTo("F");
        assertThat(loaded.getVersion()).isEqualTo(1);
    }

    /** listPaths 按 updated_at 倒序。 */
    @Test
    void listPathsOrderedByUpdatedAt() throws Exception {
        store.create(new LearningProgress("old"));
        Thread.sleep(15);
        store.create(new LearningProgress("new"));

        assertThat(store.listPaths()).extracting(LearningProgress::getPathId)
                .containsExactly("new", "old");
    }

    /** 会话读写：upsert / get / list / 偏好合并 / 移动路径 / 删除级联消息。 */
    @Test
    void sessionLifecycle() {
        Map<String, Object> session = new java.util.LinkedHashMap<>();
        session.put("session_id", "s1");
        session.put("path_id", "p");
        session.put("title", "T");
        session.put("preferences", Map.of("llm_selection", "m1"));
        store.upsertSession(session);

        assertThat(store.getSession("s1").get("title")).isEqualTo("T");
        assertThat(store.listSessions("p")).hasSize(1);

        store.setSessionPreferences("s1", Map.of("mode_hint", "study"));
        @SuppressWarnings("unchecked")
        Map<String, Object> prefs = (Map<String, Object>) store.getSession("s1").get("preferences");
        assertThat(prefs).containsEntry("llm_selection", "m1").containsEntry("mode_hint", "study");

        assertThat(store.setSessionPath("s1", "p2").get("path_id")).isEqualTo("p2");

        store.appendMessage("s1", "user", "hi", "t1", Map.of("k", "v"));
        List<Map<String, Object>> messages = store.listMessages("s1");
        assertThat(messages).hasSize(1);
        // 形状与参考实现 list_messages 一致：6 个键（不含 session_id），data 已解析为对象。
        assertThat(messages.get(0)).containsOnlyKeys(
                "id", "turn_id", "role", "content", "data", "created_at");
        assertThat(messages.get(0).get("data")).isEqualTo(Map.of("k", "v"));

        assertThat(store.deleteSession("s1")).isNotNull();
        assertThat(store.getSession("s1")).isNull();
        assertThat(store.listMessages("s1")).isEmpty();
    }
}
