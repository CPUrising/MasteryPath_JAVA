package com.masterypath.service;

import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.policy.MasteryPolicy;
import com.masterypath.storage.LearningStore;
import com.masterypath.storage.PreconditionFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 学习者声明掌握（{@code TC-C6} / {@code TC-C7} / {@code TC-C8}）。
 *
 * <p>核心约束：声明只写 {@code learnerMasteryOverrides}，<b>不写分数、不产证据</b>
 * （{@code INV-01} / {@code INV-15}）。
 */
class MasteryOverrideServiceTest {

    @TempDir
    Path tempDir;

    private LearningStore store;
    private LearningService service;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
        service = new LearningService(store);
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    @DisplayName("TC-C6：声明后掌握，来源为 learner")
    void overrideMarksLearnerSource() {
        store.create(ServiceFixtures.onePoint("p", KnowledgeType.CONCEPT));

        Map<String, Object> result = service.overrideMastery("p", "k1", "I know this");

        LearningProgress loaded = store.load("p");
        assertThat(MasteryPolicy.isMastered(loaded, loaded.findKnowledgePoint("k1").knowledgePoint()))
                .isTrue();
        assertThat(MasteryPolicy.masterySource(loaded, loaded.findKnowledgePoint("k1").knowledgePoint()))
                .isEqualTo("learner");
        assertThat(result).containsEntry("source", "learner");
    }

    @Test
    @DisplayName("TC-C7：声明不写分数")
    void overrideDoesNotWriteScore() {
        LearningProgress progress = ServiceFixtures.onePoint("p", KnowledgeType.MEMORY);
        progress.getMasteryLevels().put("k1", 0.1);
        store.create(progress);

        service.overrideMastery("p", "k1", "");

        LearningProgress loaded = store.load("p");
        assertThat(loaded.getMasteryLevels()).containsEntry("k1", 0.1);
        assertThat(MasteryPolicy.isAssessedMastered(
                loaded, loaded.findKnowledgePoint("k1").knowledgePoint())).isFalse();
    }

    @Test
    @DisplayName("TC-C8：声明不产学习证据")
    void overrideAddsNoEvidence() {
        store.create(ServiceFixtures.onePoint("p", KnowledgeType.CONCEPT));

        service.overrideMastery("p", "k1", "");

        assertThat(store.load("p").getLearningEvidence()).isEmpty();
    }

    @Test
    @DisplayName("未掌握 → 声明后 gate_cleared 为 true")
    void gateClearedWhenPreviouslyUnmastered() {
        store.create(ServiceFixtures.onePoint("p", KnowledgeType.CONCEPT));

        Map<String, Object> result = service.overrideMastery("p", "k1", "");

        assertThat(result).containsEntry("gate_cleared", true);
    }

    @Test
    @DisplayName("已由系统掌握 → 声明不重复清门，gate_cleared 为 false")
    void gateNotClearedWhenAlreadyMastered() {
        LearningProgress progress = ServiceFixtures.onePoint("p", KnowledgeType.MEMORY);
        progress.getMasteryLevels().put("k1", 1.0);
        store.create(progress);

        Map<String, Object> result = service.overrideMastery("p", "k1", "");

        assertThat(result).containsEntry("gate_cleared", false);
    }

    @Test
    @DisplayName("重复声明第二次 gate_cleared 为 false（幂等语义）")
    void secondOverrideDoesNotClearGateAgain() {
        store.create(ServiceFixtures.onePoint("p", KnowledgeType.CONCEPT));
        service.overrideMastery("p", "k1", "");

        Map<String, Object> result = service.overrideMastery("p", "k1", "again");

        assertThat(result).containsEntry("gate_cleared", false);
    }

    @Test
    @DisplayName("声明发出 mastery.override 事件，payload 含 id 与 note")
    void overrideEmitsEvent() {
        store.create(ServiceFixtures.onePoint("p", KnowledgeType.CONCEPT));

        service.overrideMastery("p", "k1", "because");

        Map<String, Object> event = store.eventsSince("p", 0).stream()
                .filter(item -> "mastery.override".equals(item.get("event_type")))
                .findFirst()
                .orElseThrow();
        assertThat(event.get("payload")).asString()
                .contains("\"knowledge_point_id\":\"k1\"")
                .contains("\"note\":\"because\"");
    }

    @Test
    @DisplayName("未知知识点用 unknown knowledge point 文案（与 defer 不同）")
    void unknownPointUsesKnowledgePointWording() {
        store.create(ServiceFixtures.onePoint("p", KnowledgeType.CONCEPT));

        assertThatThrownBy(() -> service.overrideMastery("p", "nope", ""))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("unknown knowledge point: nope");
    }

    @Test
    @DisplayName("对不存在的路径声明抛前置条件异常")
    void overrideMissingPathRejected() {
        assertThatThrownBy(() -> service.overrideMastery("nope", "k1", ""))
                .isInstanceOf(PreconditionFailedException.class);
    }

    @Test
    @DisplayName("声明后 version 递增（走了真实事务）")
    void overrideBumpsVersion() {
        store.create(ServiceFixtures.onePoint("p", KnowledgeType.CONCEPT));
        int before = store.load("p").getVersion();

        service.overrideMastery("p", "k1", "");

        assertThat(store.load("p").getVersion()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("声明后 nextObjective 越过该目标")
    void overrideAdvancesFrontier() {
        store.create(ServiceFixtures.onePoint("p", KnowledgeType.CONCEPT));

        service.overrideMastery("p", "k1", "");

        LearningProgress loaded = store.load("p");
        assertThat(MasteryPolicy.nextObjective(loaded).action()).isEqualTo("complete");
    }

    @Test
    @DisplayName("声明不改变知识类型映射")
    void overrideKeepsKnowledgeTypes() {
        LearningProgress progress = ServiceFixtures.onePoint("p", KnowledgeType.CONCEPT);
        store.create(progress);
        Map<String, KnowledgeType> before = store.load("p").getKnowledgeTypes();

        service.overrideMastery("p", "k1", "");

        assertThat(store.load("p").getKnowledgeTypes()).isEqualTo(before);
    }

    @Test
    @DisplayName("note 为 null 落为空串")
    void nullNoteBecomesEmpty() {
        store.create(ServiceFixtures.onePoint("p", KnowledgeType.CONCEPT));

        service.overrideMastery("p", "k1", null);

        assertThat(store.load("p").getLearnerMasteryOverrides().get("k1").getNote()).isEmpty();
    }

    @Test
    @DisplayName("声明不新增 quizAttempts")
    void overrideAddsNoAttempts() {
        store.create(ServiceFixtures.onePoint("p", KnowledgeType.CONCEPT));

        service.overrideMastery("p", "k1", "");

        assertThat(store.load("p").getQuizAttempts()).isEmpty();
        assertThat(store.load("p").getLearningEvidence()).isEmpty();
    }

    @Test
    @DisplayName("多知识点时只影响被声明的那个")
    void overrideAffectsOnlyTargetPoint() {
        LearningProgress progress = new LearningProgress("p");
        progress.setModules(List.of(ServiceFixtures.module("m1", "M1", 0, List.of(
                ServiceFixtures.kp("k1", "fact one", KnowledgeType.CONCEPT),
                ServiceFixtures.kp("k2", "fact two", KnowledgeType.CONCEPT)))));
        store.create(progress);

        service.overrideMastery("p", "k1", "");

        LearningProgress loaded = store.load("p");
        assertThat(MasteryPolicy.isMastered(loaded, loaded.findKnowledgePoint("k1").knowledgePoint()))
                .isTrue();
        assertThat(MasteryPolicy.isMastered(loaded, loaded.findKnowledgePoint("k2").knowledgePoint()))
                .isFalse();
    }
}
