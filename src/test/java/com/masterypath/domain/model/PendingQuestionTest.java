package com.masterypath.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** TC-A5：旧式选项字符串解析（spec 03 §A）。 */
class PendingQuestionTest {

    /** TC-A5：{@code ["A: x", "B: y"]} → {@code {A:x, B:y}}。 */
    @Test
    void legacyOptionStringsParsed() {
        PendingQuestion question = new PendingQuestion();
        question.setQuestionId("q");
        question.setKnowledgePointId("k");
        question.setOptions(List.of("A: x", "B: y"));

        assertThat(question.choiceMap()).containsEntry("A", "x").containsEntry("B", "y");
    }

    /** 无标签时按位置编号（与 pending.py 一致）。 */
    @Test
    void labelsAssignedPositionallyWhenMissing() {
        PendingQuestion question = new PendingQuestion();
        question.setOptions(List.of("x - 1 = 0", "y + 1 = 0"));

        assertThat(question.choiceMap()).containsEntry("A", "x - 1 = 0").containsEntry("B", "y + 1 = 0");
    }

    /** 四种标签分隔符都要识别。 */
    @Test
    void allLabelSeparatorsRecognized() {
        PendingQuestion question = new PendingQuestion();
        question.setOptions(List.of("A: one", "B. two", "C) three", "D、four"));

        assertThat(question.choiceMap())
                .containsEntry("A", "one")
                .containsEntry("B", "two")
                .containsEntry("C", "three")
                .containsEntry("D", "four");
    }

    /** 新式对象形态照常可用。 */
    @Test
    void structuredOptionsAccepted() {
        PendingQuestion question = new PendingQuestion();
        question.setOptions(List.of(new PendingOption("A", "x"), new PendingOption("B", "y")));

        assertThat(question.choiceMap()).containsEntry("A", "x").containsEntry("B", "y");
    }
}
