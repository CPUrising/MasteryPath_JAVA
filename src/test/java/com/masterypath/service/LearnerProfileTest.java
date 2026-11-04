package com.masterypath.service;

import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.storage.LearningStore;
import com.masterypath.storage.PreconditionFailedException;
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
 * 学习者画像（{@code ADR-020}，{@code TC-P1}…{@code TC-P2}）。
 *
 * <p>对照标准：{@code ../MasteryPath/tests/test_repair_profile.py} 的后 2 个用例。
 * 核心：<b>合并而非替换</b>，且无变更时不发事件。
 */
class LearnerProfileTest {

    @TempDir
    Path tempDir;

    private LearningStore store;
    private LearningService service;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
        service = new LearningService(store);
        store.create(ServiceFixtures.onePoint("p", KnowledgeType.MEMORY));
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    // -- TC-P1 合并与可见 -----------------------------------------------------

    @Test
    @DisplayName("TC-P1：首次写入返回全部变更字段，画像可从 getState 读出")
    void mergesAndIsVisible() {
        LearningService.ProfileUpdate first = service.recordLearnerProfile("p",
                fields("prior_knowledge", "knows algebra", "time_budget", "2 weeks"), "", "");

        assertThat(first.changed()).containsExactlyInAnyOrder("prior_knowledge", "time_budget");
        assertThat(first.profile()).containsEntry("prior_knowledge", "knows algebra");

        LearningService.ProfileUpdate second = service.recordLearnerProfile("p",
                fields("time_budget", "1 week"), "", "");

        assertThat(second.changed()).containsExactly("time_budget");
        assertThat(second.profile()).containsEntry("prior_knowledge", "knows algebra")
                .containsEntry("time_budget", "1 week");
        assertThat(service.getState("p").get("learner_profile"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("time_budget", "1 week");
        assertThat(eventTypes()).contains("path.learner_profile_recorded");
    }

    @Test
    @DisplayName("TC-P1：合并而非替换——只写一个字段时其余保留")
    void mergeKeepsOmittedFields() {
        service.recordLearnerProfile("p",
                fields("prior_knowledge", "knows algebra", "notes", "likes examples"), "", "");

        LearningService.ProfileUpdate update = service.recordLearnerProfile("p",
                fields("target_level", "advanced"), "", "");

        assertThat(update.profile()).containsEntry("prior_knowledge", "knows algebra")
                .containsEntry("notes", "likes examples")
                .containsEntry("target_level", "advanced");
    }

    @Test
    @DisplayName("写入时记录 updated_at")
    void recordsUpdatedAt() {
        LearningService.ProfileUpdate update = service.recordLearnerProfile("p",
                fields("notes", "x"), "", "");

        assertThat(update.profile().get("updated_at")).isInstanceOf(Double.class);
    }

    @Test
    @DisplayName("画像持久化到聚合（重新 load 仍在）")
    void profilePersisted() {
        service.recordLearnerProfile("p", fields("notes", "prefers examples"), "", "");

        LearningProgress reloaded = store.load("p");
        assertThat(reloaded.getLearnerProfile()).containsEntry("notes", "prefers examples");
    }

    // -- TC-P2 边界 -----------------------------------------------------------

    @Test
    @DisplayName("TC-P2：无变更时不发事件（重复提交同一份画像）")
    void noChangeEmitsNoEvent() {
        service.recordLearnerProfile("p", fields("notes", "same"), "", "");
        int before = store.eventsSince("p", 0).size();

        LearningService.ProfileUpdate update = service.recordLearnerProfile("p",
                fields("notes", "same"), "", "");

        assertThat(update.changed()).isEmpty();
        assertThat(store.eventsSince("p", 0)).hasSize(before);
    }

    @Test
    @DisplayName("白名单外的键被静默丢弃")
    void unknownKeysSilentlyDropped() {
        LearningService.ProfileUpdate update = service.recordLearnerProfile("p",
                fields("notes", "keep", "secret_hobby", "dropped"), "", "");

        assertThat(update.changed()).containsExactly("notes");
        assertThat(update.profile()).doesNotContainKey("secret_hobby");
    }

    @Test
    @DisplayName("全部字段都在白名单外时拒绝，文案逐字")
    void allFieldsRejectedWhenNoneAllowed() {
        assertThatThrownBy(() -> service.recordLearnerProfile("p",
                fields("secret_hobby", "x"), "", ""))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("learner profile needs at least one field");
    }

    @Test
    @DisplayName("空 map 拒绝，文案逐字")
    void emptyFieldsRejected() {
        assertThatThrownBy(() -> service.recordLearnerProfile("p", Map.of(), "", ""))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("learner profile needs at least one field");
    }

    @Test
    @DisplayName("teaching_strategy 只接受 probe_first / teach_first")
    void teachingStrategyValidated() {
        assertThatThrownBy(() -> service.recordLearnerProfile("p",
                fields("teaching_strategy", "freestyle"), "", ""))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("teaching_strategy must be probe_first or teach_first");

        assertThat(service.recordLearnerProfile("p",
                fields("teaching_strategy", "teach_first"), "", "").changed())
                .containsExactly("teaching_strategy");
    }

    @Test
    @DisplayName("值为 null 的字段被跳过")
    void nullValuesSkipped() {
        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("notes", null);
        incoming.put("target_level", "beginner");

        LearningService.ProfileUpdate update = service.recordLearnerProfile("p", incoming, "", "");

        assertThat(update.changed()).containsExactly("target_level");
    }

    @Test
    @DisplayName("超长值截断到 2000 字符")
    void longValueTruncated() {
        String longText = "x".repeat(2500);

        LearningService.ProfileUpdate update = service.recordLearnerProfile("p",
                fields("notes", longText), "", "");

        assertThat((String) update.profile().get("notes")).hasSize(2000);
    }

    @Test
    @DisplayName("事件 payload 含 fields（变更字段名列表）")
    void eventPayloadHasChangedFields() {
        service.recordLearnerProfile("p", fields("notes", "a", "time_budget", "2w"), "", "");

        String payload = store.eventsSince("p", 0).stream()
                .filter(event -> "path.learner_profile_recorded".equals(event.get("event_type")))
                .map(event -> String.valueOf(event.get("payload")))
                .findFirst()
                .orElseThrow();
        assertThat(payload).contains("notes").contains("time_budget");
    }

    @Test
    @DisplayName("可对不存在的路径直接建档（create_if_missing）")
    void createsPathIfMissing() {
        LearningService.ProfileUpdate update = service.recordLearnerProfile("fresh",
                fields("notes", "new path"), "", "");

        assertThat(update.changed()).containsExactly("notes");
        assertThat(store.load("fresh")).isNotNull();
    }

    // -- 辅助 ----------------------------------------------------------------

    private static Map<String, Object> fields(String... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            map.put(keyValues[index], keyValues[index + 1]);
        }
        return map;
    }

    private List<String> eventTypes() {
        return store.eventsSince("p", 0).stream()
                .map(event -> String.valueOf(event.get("event_type")))
                .toList();
    }
}
