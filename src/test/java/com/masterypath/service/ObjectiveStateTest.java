package com.masterypath.service;

import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.policy.MasteryPolicy;
import com.masterypath.domain.policy.NextStep;
import com.masterypath.storage.LearningStore;
import com.masterypath.storage.PreconditionFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 目标状态：延后（defer）与已讲解（note explained）。
 *
 * <p>两者都<b>不给掌握学分</b>，且错误文案是 {@code unknown objective:}（与 override 不同）。
 */
class ObjectiveStateTest {

    @TempDir
    Path tempDir;

    private LearningStore store;
    private LearningService service;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
        service = new LearningService(store);
        store.create(ServiceFixtures.onePoint("p", KnowledgeType.CONCEPT));
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    // -- defer ----------------------------------------------------------------

    @Test
    @DisplayName("延后写入 deferredObjectives 并返回 deferred=true")
    void deferWritesRecord() {
        Map<String, Object> result = service.deferObjective("p", "k1", "later");

        assertThat(result).containsEntry("knowledge_point_id", "k1").containsEntry("deferred", true);
        assertThat(store.load("p").getDeferredObjectives()).containsKey("k1");
        assertThat(store.load("p").getDeferredObjectives().get("k1").getNote()).isEqualTo("later");
    }

    @Test
    @DisplayName("延后不发掌握；只剩延后目标时仍作为回退返回")
    void deferSkipsPoint() {
        service.deferObjective("p", "k1", "");

        LearningProgress loaded = store.load("p");
        assertThat(MasteryPolicy.isMastered(loaded, loaded.findKnowledgePoint("k1").knowledgePoint()))
                .isFalse();
        NextStep step = MasteryPolicy.nextObjective(loaded);
        assertThat(step.action()).isEqualTo("probe");
        assertThat(step.knowledgePointId()).isEqualTo("k1");
    }

    @Test
    @DisplayName("延后事件 payload 只有 knowledge_point_id（不含 note）")
    void deferEventOmitsNote() {
        service.deferObjective("p", "k1", "secret note");

        Map<String, Object> event = store.eventsSince("p", 0).stream()
                .filter(item -> "objective.deferred".equals(item.get("event_type")))
                .findFirst()
                .orElseThrow();
        String payload = String.valueOf(event.get("payload"));
        assertThat(payload).contains("\"knowledge_point_id\":\"k1\"");
        assertThat(payload).doesNotContain("secret note");
        assertThat(payload).doesNotContain("\"note\"");
    }

    @Test
    @DisplayName("延后不写分数、不产证据")
    void deferAddsNoEvidence() {
        service.deferObjective("p", "k1", "");

        LearningProgress loaded = store.load("p");
        assertThat(loaded.getMasteryLevels()).isEmpty();
        assertThat(loaded.getLearningEvidence()).isEmpty();
        assertThat(loaded.getQuizAttempts()).isEmpty();
    }

    @Test
    @DisplayName("延后未知目标用 unknown objective 文案")
    void deferUnknownUsesObjectiveWording() {
        assertThatThrownBy(() -> service.deferObjective("p", "nope", ""))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("unknown objective: nope");
    }

    // -- noteExplained --------------------------------------------------------

    @Test
    @DisplayName("记录已讲解写入 explainedObjectives，返回 mastery_credit=false")
    void noteExplainedWritesRecord() {
        Map<String, Object> result = service.noteExplained("p", "k1", "covered the basics");

        assertThat(result).containsEntry("knowledge_point_id", "k1")
                .containsEntry("mastery_credit", false);
        assertThat(store.load("p").getExplainedObjectives()).containsKey("k1");
        assertThat(store.load("p").getExplainedObjectives().get("k1"))
                .containsEntry("summary", "covered the basics");
    }

    @Test
    @DisplayName("已讲解不给掌握学分")
    void noteExplainedGrantsNoMastery() {
        service.noteExplained("p", "k1", "summary");

        LearningProgress loaded = store.load("p");
        assertThat(MasteryPolicy.isMastered(loaded, loaded.findKnowledgePoint("k1").knowledgePoint()))
                .isFalse();
        assertThat(loaded.getMasteryLevels()).isEmpty();
        assertThat(loaded.getLearningEvidence()).isEmpty();
    }

    @Test
    @DisplayName("已讲解事件 payload 含 summary（与 defer 不对称）")
    void noteExplainedEventIncludesSummary() {
        service.noteExplained("p", "k1", "the summary");

        Map<String, Object> event = store.eventsSince("p", 0).stream()
                .filter(item -> "objective.explained".equals(item.get("event_type")))
                .findFirst()
                .orElseThrow();
        assertThat(String.valueOf(event.get("payload")))
                .contains("\"knowledge_point_id\":\"k1\"")
                .contains("\"summary\":\"the summary\"");
    }

    @Test
    @DisplayName("已讲解未知目标用 unknown objective 文案")
    void noteExplainedUnknownUsesObjectiveWording() {
        assertThatThrownBy(() -> service.noteExplained("p", "nope", "x"))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("unknown objective: nope");
    }

    @Test
    @DisplayName("summary 为 null 落为空串")
    void nullSummaryBecomesEmpty() {
        service.noteExplained("p", "k1", null);

        assertThat(store.load("p").getExplainedObjectives().get("k1"))
                .containsEntry("summary", "");
    }

    @Test
    @DisplayName("延后与已讲解互不影响")
    void deferAndExplainedAreIndependent() {
        service.deferObjective("p", "k1", "later");
        service.noteExplained("p", "k1", "taught");

        LearningProgress loaded = store.load("p");
        assertThat(loaded.getDeferredObjectives()).containsKey("k1");
        assertThat(loaded.getExplainedObjectives()).containsKey("k1");
    }

    @Test
    @DisplayName("对不存在的路径延后抛前置条件异常")
    void deferMissingPathRejected() {
        assertThatThrownBy(() -> service.deferObjective("nope", "k1", ""))
                .isInstanceOf(PreconditionFailedException.class);
    }
}
