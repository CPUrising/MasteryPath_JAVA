package com.masterypath.storage;

import com.masterypath.domain.model.MasteryInteraction;
import com.masterypath.domain.model.QuestionBankEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 路径删除的级联范围（{@code LearningStore.deletePath}）。
 *
 * <p>核心：删除路径 MUST 连带清掉 messages / sessions / question_bank / interactions / paths，
 * 但 MUST NOT 删除 events（append-only 审计日志）。
 */
@Tag("storage")
class PathDeletionTest {

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

    private void seedPath(String pathId) {
        store.mutate(pathId, tx -> {
            tx.emit("path.created", Map.of("goal", "", "name", pathId));
            return null;
        }, null, true);
    }

    private Map<String, Object> session(String sessionId, String pathId) {
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("session_id", sessionId);
        session.put("path_id", pathId);
        session.put("title", "T");
        session.put("mode", "study");
        return store.upsertSession(session);
    }

    private MasteryInteraction interaction(String pathId, String interactionId) {
        MasteryInteraction interaction = new MasteryInteraction();
        interaction.setInteractionId(interactionId);
        interaction.setPathId(pathId);
        interaction.setStatus(com.masterypath.domain.model.InteractionStatus.REGISTERED);
        return store.saveInteraction(interaction);
    }

    private QuestionBankEntry bankEntry(String pathId, String entryId) {
        QuestionBankEntry entry = new QuestionBankEntry();
        entry.setEntryId(entryId);
        entry.setPathId(pathId);
        entry.setQuestionId("q-" + entryId);
        entry.setKnowledgePointId("k1");
        return store.saveBankEntry(entry);
    }

    @Test
    @DisplayName("删除路径返回 true，路径读取为 null")
    void deleteRemovesPathRow() {
        seedPath("p");

        assertThat(store.deletePath("p")).isTrue();
        assertThat(store.load("p")).isNull();
    }

    @Test
    @DisplayName("删除不存在的路径返回 false")
    void deleteMissingReturnsFalse() {
        assertThat(store.deletePath("nope")).isFalse();
    }

    @Test
    @DisplayName("级联删除会话与其消息")
    void cascadeRemovesSessionsAndMessages() {
        seedPath("p");
        session("s1", "p");
        store.appendMessage("s1", "user", "hi", "t1", Map.of());

        store.deletePath("p");

        assertThat(store.getSession("s1")).isNull();
        assertThat(store.listMessages("s1")).isEmpty();
    }

    @Test
    @DisplayName("级联删除题库与交互记录")
    void cascadeRemovesBankAndInteractions() {
        seedPath("p");
        interaction("p", "i1");
        bankEntry("p", "e1");

        store.deletePath("p");

        assertThat(store.getInteraction("p", "i1")).isNull();
        assertThat(store.listBankEntries("p")).isEmpty();
    }

    @Test
    @DisplayName("删除路径 MUST NOT 删除 events（append-only 审计日志）")
    void eventsSurviveDeletion() {
        seedPath("p");

        store.deletePath("p");

        List<Map<String, Object>> events = store.eventsSince("p", 0);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("event_type")).isEqualTo("path.created");
    }

    @Test
    @DisplayName("删除只影响目标路径，其他路径完好")
    void deleteIsScopedToOnePath() {
        seedPath("p");
        seedPath("other");
        session("s1", "p");
        session("s2", "other");

        store.deletePath("p");

        assertThat(store.load("p")).isNull();
        assertThat(store.load("other")).isNotNull();
        assertThat(store.getSession("s1")).isNull();
        assertThat(store.getSession("s2")).isNotNull();
    }

    @Test
    @DisplayName("删除后其他路径的事件仍可读")
    void otherPathEventsUntouched() {
        seedPath("p");
        seedPath("other");

        store.deletePath("p");

        assertThat(store.eventsSince("other", 0)).hasSize(1);
    }

    @Test
    @DisplayName("无从属数据时删除同样成功")
    void deletePathWithNoChildren() {
        seedPath("p");

        assertThat(store.deletePath("p")).isTrue();
        assertThat(store.listPaths()).isEmpty();
    }
}
