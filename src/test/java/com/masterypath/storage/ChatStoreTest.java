package com.masterypath.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 聊天存储（{@code ../MasteryPath/tests/test_preferences.py#test_chat_session_preferences_and_legacy_model_id}）。
 *
 * <p>重点断言三件事：DDL 与参考实现逐字一致、独立于 {@code learning.db}、
 * {@code updateSession} 的三条归一（kb_ids 保留 / preferences 合并 / model_id 回写）。
 */
@Tag("storage")
class ChatStoreTest {

    @TempDir
    Path tempDir;

    private ChatStore store;

    @BeforeEach
    void setUp() {
        store = new ChatStore(tempDir);
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    @DisplayName("DDL 列名与顺序与参考实现一致")
    void schemaMatchesReference() {
        assertThat(store.tableColumns("sessions")).containsExactly(
                "session_id", "title", "model_id", "kb_ids", "preferences",
                "created_at", "updated_at");
        assertThat(store.tableColumns("messages")).containsExactly(
                "id", "session_id", "role", "content", "attachments", "created_at");
    }

    @Test
    @DisplayName("独立 chat.db，不写 learning.db")
    void usesSeparateDatabaseFile() {
        assertThat(store.dbPath().getFileName().toString()).isEqualTo("chat.db");
        assertThat(tempDir.resolve("learning.db")).doesNotExist();
    }

    @Test
    @DisplayName("会话 id 形如 c + 12 位 hex")
    void sessionIdHasChatPrefix() {
        Map<String, Object> session = store.createSession("", "", List.of(), Map.of());

        assertThat(String.valueOf(session.get("session_id"))).matches("^c[0-9a-f]{12}$");
    }

    @Test
    @DisplayName("未知会话抛 UnknownSession")
    void unknownSessionThrows() {
        assertThatThrownBy(() -> store.getSession("nope"))
                .isInstanceOf(ChatStore.UnknownSession.class);
        assertThatThrownBy(() -> store.updateSession("nope", Map.of()))
                .isInstanceOf(ChatStore.UnknownSession.class);
        assertThatThrownBy(() -> store.deleteSession("nope"))
                .isInstanceOf(ChatStore.UnknownSession.class);
    }

    @Test
    @DisplayName("getSession 不含 message_count / last_message；listSessions 才有")
    void listSessionsAddsDerivedFields() {
        Map<String, Object> session = store.createSession("t", "", List.of(), Map.of());
        String sessionId = String.valueOf(session.get("session_id"));
        store.addMessage(sessionId, "user", "hello", List.of());

        assertThat(store.getSession(sessionId))
                .doesNotContainKeys("message_count", "last_message");

        List<Map<String, Object>> listed = store.listSessions();
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0)).containsEntry("message_count", 1)
                .containsEntry("last_message", "hello");
    }

    @Test
    @DisplayName("preferences 部分更新合并，llm_selection 回写 legacy model_id")
    void preferencesMergeAndMirrorModelId() {
        Map<String, Object> session = store.createSession("", "",
                List.of(), Map.of("llm_selection", Map.of("provider_id", "p", "model_id", "m1")));
        String sessionId = String.valueOf(session.get("session_id"));

        Map<String, Object> updated = store.updateSession(sessionId,
                Map.of("preferences", Map.of("knowledge_bases", List.of("kb-1"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> preferences = (Map<String, Object>) updated.get("preferences");
        assertThat(preferences).containsEntry("knowledge_bases", List.of("kb-1"));
        // llm_selection 未被整体替换掉。
        assertThat(preferences).containsKey("llm_selection");
        assertThat(updated).containsEntry("model_id", "m1");
    }

    @Test
    @DisplayName("kb_ids 非列表时保留原值")
    void nonListKbIdsKeepsExisting() {
        Map<String, Object> session = store.createSession("", "", List.of("kb-a"), Map.of());
        String sessionId = String.valueOf(session.get("session_id"));

        Map<String, Object> updated = store.updateSession(sessionId, Map.of("kb_ids", "junk"));

        assertThat(updated).containsEntry("kb_ids", List.of("kb-a"));
    }

    @Test
    @DisplayName("删除会话连带删除消息")
    void deleteRemovesMessages() {
        Map<String, Object> session = store.createSession("", "", List.of(), Map.of());
        String sessionId = String.valueOf(session.get("session_id"));
        store.addMessage(sessionId, "user", "hi", List.of());

        store.deleteSession(sessionId);

        assertThatThrownBy(() -> store.getSession(sessionId))
                .isInstanceOf(ChatStore.UnknownSession.class);
        assertThat(store.listMessages(sessionId)).isEmpty();
    }

    @Test
    @DisplayName("撤回按 id 截断：删除该条及其之后的所有消息")
    void deleteMessagesFromTruncatesTail() {
        Map<String, Object> session = store.createSession("", "", List.of(), Map.of());
        String sessionId = String.valueOf(session.get("session_id"));
        long first = ((Number) store.addMessage(sessionId, "user", "q1", List.of()).get("id")).longValue();
        store.addMessage(sessionId, "assistant", "a1", List.of());
        store.addMessage(sessionId, "user", "q2", List.of());
        store.addMessage(sessionId, "assistant", "a2", List.of());

        int deleted = store.deleteMessagesFrom(sessionId, first);

        assertThat(deleted).isEqualTo(4);
        assertThat(store.listMessages(sessionId)).isEmpty();
        assertThatThrownBy(() -> store.deleteMessagesFrom("ghost", first))
                .isInstanceOf(ChatStore.UnknownSession.class);
    }

    @Test
    @DisplayName("撤回保留更早的消息，只截断目标及其之后")
    void deleteMessagesFromKeepsEarlier() {
        Map<String, Object> session = store.createSession("", "", List.of(), Map.of());
        String sessionId = String.valueOf(session.get("session_id"));
        store.addMessage(sessionId, "user", "q1", List.of());
        store.addMessage(sessionId, "assistant", "a1", List.of());
        long third = ((Number) store.addMessage(sessionId, "user", "q2", List.of()).get("id")).longValue();
        store.addMessage(sessionId, "assistant", "a2", List.of());

        int deleted = store.deleteMessagesFrom(sessionId, third);

        assertThat(deleted).isEqualTo(2);
        assertThat(store.listMessages(sessionId))
                .extracting(row -> row.get("content"))
                .containsExactly("q1", "a1");
    }

    @Test
    @DisplayName("addMessage 刷新会话 updated_at")
    void addMessageTouchesSession() throws Exception {
        Map<String, Object> session = store.createSession("", "", List.of(), Map.of());
        String sessionId = String.valueOf(session.get("session_id"));
        double before = ((Number) session.get("updated_at")).doubleValue();
        Thread.sleep(10);

        store.addMessage(sessionId, "user", "hi", List.of());

        double after = ((Number) store.getSession(sessionId).get("updated_at")).doubleValue();
        assertThat(after).isGreaterThan(before);
    }
}
