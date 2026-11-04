package com.masterypath.service;

import com.masterypath.domain.model.AssessmentResult;
import com.masterypath.domain.model.AssessmentType;
import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningEvidence;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.domain.model.QuestionBankEntry;
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
 * 定性评估（{@code TC-M3} / {@code INV-05}）。
 *
 * <p>核心：{@code concept} / {@code design} 只能靠这里打开；评估从不写分数。
 */
class QualitativeAssessTest {

    @TempDir
    Path tempDir;

    private LearningStore store;
    private LearningService service;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
        service = new LearningService(store);
        // k1 = concept（可评估），k2 = memory（不可评估）
        store.create(ServiceFixtures.onePoint("p", KnowledgeType.CONCEPT));
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    private void addMemoryPoint() {
        store.mutate("p", tx -> {
            com.masterypath.domain.model.KnowledgePoint memory =
                    new com.masterypath.domain.model.KnowledgePoint(
                            "k2", "fact one", KnowledgeType.MEMORY, "m1");
            memory.setSourceIds(List.of("s1"));
            tx.progress().getModules().get(0).getKnowledgePoints().add(memory);
            tx.progress().getKnowledgeTypes().put("k2", KnowledgeType.MEMORY);
            return tx.progress();
        });
    }

    // -- TC-M3 ----------------------------------------------------------------

    @Test
    @DisplayName("TC-M3：concept 通过后门槛清除，来源为 system")
    void passClearsGate() {
        Map<String, Object> result = service.assess("p", "k1", true,
                "explained in own words", null, false, "", "");

        assertThat(result).containsEntry("status", "assessed")
                .containsEntry("passed", true)
                .containsEntry("gate_cleared", true)
                .containsEntry("source", "system");
    }

    @Test
    @DisplayName("TC-M3：concept 未通过时门槛不清除")
    void failDoesNotClearGate() {
        Map<String, Object> result = service.assess("p", "k1", false, "", null, false, "", "");

        assertThat(result).containsEntry("gate_cleared", false);
        LearningProgress loaded = store.load("p");
        assertThat(MasteryPolicy.isMastered(loaded,
                loaded.findKnowledgePoint("k1").knowledgePoint())).isFalse();
    }

    @Test
    @DisplayName("TC-M3：对 memory 评估被拒绝，文案逐字")
    void refusesQuantitativeObjective() {
        addMemoryPoint();

        assertThatThrownBy(() -> service.assess("p", "k2", true, "", null, false, "", ""))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("mastery_assess is for concept/design objectives; "
                        + "use mastery_grade for memory/procedure");
    }

    @Test
    @DisplayName("TC-M3：拒绝时聚合零变更")
    void refusalLeavesNoTrace() {
        addMemoryPoint();

        assertThatThrownBy(() -> service.assess("p", "k2", true, "", null, false, "", ""))
                .isInstanceOf(PreconditionFailedException.class);

        LearningProgress loaded = store.load("p");
        assertThat(loaded.getQualitativeMastery()).isEmpty();
        assertThat(loaded.getLearningEvidence()).isEmpty();
    }

    @Test
    @DisplayName("未知知识点被拒绝")
    void unknownObjectiveRejected() {
        assertThatThrownBy(() -> service.assess("p", "nope", true, "", null, false, "", ""))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("unknown objective: nope");
    }

    // -- 证据 ----------------------------------------------------------------

    @Test
    @DisplayName("证据类型固定为 QUALITATIVE")
    void evidenceTypeIsQualitative() {
        service.assess("p", "k1", true, "", null, false, "", "");

        LearningEvidence evidence = store.load("p").getLearningEvidence().get(0);
        assertThat(evidence.getAssessmentType()).isEqualTo(AssessmentType.QUALITATIVE);
    }

    @Test
    @DisplayName("通过时证据结果为 correct")
    void passRecordsCorrect() {
        service.assess("p", "k1", true, "", null, false, "", "");

        assertThat(store.load("p").getLearningEvidence().get(0).getResult())
                .isEqualTo(AssessmentResult.CORRECT);
    }

    @Test
    @DisplayName("未通过时证据结果为 partial（不是 incorrect）")
    void failRecordsPartial() {
        service.assess("p", "k1", false, "", null, false, "", "");

        assertThat(store.load("p").getLearningEvidence().get(0).getResult())
                .isEqualTo(AssessmentResult.PARTIAL);
    }

    @Test
    @DisplayName("多次评估累积证据")
    void multipleAssessmentsAccumulate() {
        service.assess("p", "k1", false, "", null, false, "", "");
        service.assess("p", "k1", true, "now correct", null, false, "", "");

        LearningProgress loaded = store.load("p");
        assertThat(loaded.getLearningEvidence()).hasSize(2);
        assertThat(loaded.getQualitativeMastery()).containsEntry("k1", true);
    }

    @Test
    @DisplayName("证据不写 hintsUsed，也不写 session/turn 之外的多余字段")
    void evidenceFieldsAreMinimal() {
        service.assess("p", "k1", true, "", null, false, "s1", "t1");

        LearningEvidence evidence = store.load("p").getLearningEvidence().get(0);
        assertThat(evidence.getHintsUsed()).isZero();
        assertThat(evidence.getSessionId()).isEqualTo("s1");
        assertThat(evidence.getTurnId()).isEqualTo("t1");
        assertThat(evidence.getSource()).isEqualTo("mastery_path");
        assertThat(evidence.getEvidenceId()).hasSize(32);
    }

    // -- quality -------------------------------------------------------------

    @Test
    @DisplayName("通过时 quality 固定 1.0（忽略传入值）")
    void passIgnoresIncomingQuality() {
        service.assess("p", "k1", true, "", 0.5, false, "", "");

        assertThat(store.load("p").getLearningEvidence().get(0).getQuality()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("未通过且未传 quality 时默认 0.2")
    void failDefaultsToZeroPointTwo() {
        service.assess("p", "k1", false, "", null, false, "", "");

        assertThat(store.load("p").getLearningEvidence().get(0).getQuality()).isEqualTo(0.2);
    }

    @Test
    @DisplayName("未通过且传入 quality 时使用传入值")
    void failUsesIncomingQuality() {
        service.assess("p", "k1", false, "", 0.35, false, "", "");

        assertThat(store.load("p").getLearningEvidence().get(0).getQuality()).isEqualTo(0.35);
    }

    // -- INV-05 ---------------------------------------------------------------

    @Test
    @DisplayName("评估从不写 masteryLevels（INV-05）")
    void neverWritesMasteryLevels() {
        service.assess("p", "k1", true, "", null, false, "", "");

        assertThat(store.load("p").getMasteryLevels()).isEmpty();
    }

    @Test
    @DisplayName("评估写 qualitativeMastery")
    void writesQualitativeMastery() {
        service.assess("p", "k1", true, "", null, false, "", "");

        assertThat(store.load("p").getQualitativeMastery()).containsEntry("k1", true);
    }

    @Test
    @DisplayName("先通过后未通过会把定性标记改回 false")
    void laterFailureOverwritesFlag() {
        service.assess("p", "k1", true, "", null, false, "", "");
        service.assess("p", "k1", false, "", null, false, "", "");

        assertThat(store.load("p").getQualitativeMastery()).containsEntry("k1", false);
    }

    // -- 事件与前沿 -----------------------------------------------------------

    @Test
    @DisplayName("评估发 qualitative.assessed；通过时再发 objective.mastered")
    void emitsEvents() {
        service.assess("p", "k1", true, "", null, false, "", "");

        assertThat(store.eventsSince("p", 0))
                .extracting(event -> event.get("event_type"))
                .contains("qualitative.assessed", "objective.mastered");
    }

    @Test
    @DisplayName("未通过不发 objective.mastered")
    void failEmitsNoMastered() {
        service.assess("p", "k1", false, "", null, false, "", "");

        assertThat(store.eventsSince("p", 0))
                .extracting(event -> event.get("event_type"))
                .doesNotContain("objective.mastered");
    }

    @Test
    @DisplayName("通过后 nextObjective 推进（单目标场景返回 complete）")
    void passAdvancesFrontier() {
        service.assess("p", "k1", true, "", null, false, "", "");

        assertThat(MasteryPolicy.nextObjective(store.load("p")).action()).isEqualTo("complete");
    }

    // -- 待答题清空 -----------------------------------------------------------

    @Test
    @DisplayName("评估同一知识点时清空待答题")
    void clearsPendingQuestionOfSamePoint() {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q1");
        pending.setKnowledgePointId("k1");
        pending.setPrompt("explain");
        pending.setQuestionType(PendingQuestion.QuestionType.OPEN);
        service.registerQuestion("p", pending, "", "");

        service.assess("p", "k1", true, "", null, false, "", "");

        assertThat(store.load("p").getPendingQuestion()).isNull();
    }

    @Test
    @DisplayName("评估其他知识点时不清空待答题")
    void keepsPendingQuestionOfOtherPoint() {
        store.mutate("p", tx -> {
            com.masterypath.domain.model.KnowledgePoint other =
                    new com.masterypath.domain.model.KnowledgePoint(
                            "k9", "other idea", KnowledgeType.CONCEPT, "m1");
            other.setSourceIds(List.of("s1"));
            tx.progress().getModules().get(0).getKnowledgePoints().add(other);
            tx.progress().getKnowledgeTypes().put("k9", KnowledgeType.CONCEPT);
            return tx.progress();
        });
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q1");
        pending.setKnowledgePointId("k9");
        pending.setPrompt("explain");
        pending.setQuestionType(PendingQuestion.QuestionType.OPEN);
        service.registerQuestion("p", pending, "", "");

        service.assess("p", "k1", true, "", null, false, "", "");

        assertThat(store.load("p").getPendingQuestion()).isNotNull();
        assertThat(store.load("p").getPendingQuestion().getKnowledgePointId()).isEqualTo("k9");
    }

    // -- 题库 ----------------------------------------------------------------

    @Test
    @DisplayName("评估写入题库，题型为 written，题目用知识点名")
    void writesBankEntry() {
        service.assess("p", "k1", true, "my explanation", null, false, "", "");

        List<QuestionBankEntry> entries = store.listBankEntries("p");
        assertThat(entries).hasSize(1);
        QuestionBankEntry entry = entries.get(0);
        assertThat(entry.getQuestionType()).isEqualTo("written");
        assertThat(entry.getQuestion()).isEqualTo("fact one");
        assertThat(entry.getUserAnswer()).isEqualTo("my explanation");
        assertThat(entry.isCorrect()).isTrue();
        assertThat(entry.getResult()).isEqualTo("correct");
    }

    @Test
    @DisplayName("未通过的题库结果记为 partial")
    void failedBankEntryIsPartial() {
        service.assess("p", "k1", false, "", null, false, "", "");

        QuestionBankEntry entry = store.listBankEntries("p").get(0);
        assertThat(entry.isCorrect()).isFalse();
        assertThat(entry.getResult()).isEqualTo("partial");
    }

    @Test
    @DisplayName("design 类型同样可评估")
    void designTypeAssessable() {
        store.create(ServiceFixtures.onePoint("p2", KnowledgeType.DESIGN));

        Map<String, Object> result = service.assess("p2", "k1", true, "", null, false, "", "");

        assertThat(result).containsEntry("gate_cleared", true);
    }

    @Test
    @DisplayName("procedure 类型被拒绝")
    void procedureRefused() {
        store.create(ServiceFixtures.onePoint("p3", KnowledgeType.PROCEDURE));

        assertThatThrownBy(() -> service.assess("p3", "k1", true, "", null, false, "", ""))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessageContaining("concept/design");
    }
}
