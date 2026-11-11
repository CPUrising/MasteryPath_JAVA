package com.masterypath.domain.bank;

import com.masterypath.domain.grading.Decision;
import com.masterypath.domain.model.QuestionBankEntry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 题库刷题判分：可刷题判定与确定性判分（{@code 0046}）。 */
class QuestionBankGradingTest {

    @Test
    @DisplayName("short 题精确命中判对")
    void shortExact() {
        QuestionBankEntry entry = entry("short", "4");
        assertThat(QuestionBankGrading.practiceable(entry)).isTrue();
        assertThat(QuestionBankGrading.grade(entry, "4").result())
                .isEqualTo(Decision.CORRECT);
    }

    @Test
    @DisplayName("short 题接受归一化后的等价作答")
    void shortNormalised() {
        QuestionBankEntry entry = entry("short", "java thread");
        assertThat(QuestionBankGrading.grade(entry, " Java   Thread ").result())
                .isEqualTo(Decision.CORRECT);
    }

    @Test
    @DisplayName("short 题答错判错")
    void shortIncorrect() {
        assertThat(QuestionBankGrading.grade(entry("short", "4"), "999").result())
                .isEqualTo(Decision.INCORRECT);
    }

    @Test
    @DisplayName("空作答是 ungraded，不是错误")
    void shortEmptyIsUngraded() {
        assertThat(QuestionBankGrading.grade(entry("short", "4"), "  ").result())
                .isEqualTo(Decision.UNGRADED);
    }

    @Test
    @DisplayName("choice 题按选项标签判分")
    void choiceByLabel() {
        QuestionBankEntry entry = choice("A");
        assertThat(QuestionBankGrading.grade(entry, "a").result()).isEqualTo(Decision.CORRECT);
        assertThat(QuestionBankGrading.grade(entry, "B").result()).isEqualTo(Decision.INCORRECT);
    }

    @Test
    @DisplayName("choice 题无法解析选项时 ungraded")
    void choiceUnresolvedIsUngraded() {
        assertThat(QuestionBankGrading.grade(choice("A"), "不知道").result())
                .isEqualTo(Decision.UNGRADED);
    }

    @Test
    @DisplayName("written / open / 缺答案 / 选项不匹配一律不可刷")
    void notPracticeable() {
        assertThat(QuestionBankGrading.practiceable(entry("written", ""))).isFalse();
        assertThat(QuestionBankGrading.practiceable(entry("open", "任意"))).isFalse();
        assertThat(QuestionBankGrading.practiceable(entry("short", "  "))).isFalse();
        assertThat(QuestionBankGrading.practiceable(choice("C"))).isFalse();
        assertThat(QuestionBankGrading.practiceable(entry("choice", "A"))).isFalse();
        assertThat(QuestionBankGrading.practiceable(null)).isFalse();
    }

    @Test
    @DisplayName("不可刷题的判分固定为 ungraded")
    void gradeNotPracticeable() {
        Decision decision = QuestionBankGrading.grade(entry("written", ""), "some text");
        assertThat(decision.result()).isEqualTo(Decision.UNGRADED);
        assertThat(decision.reason()).isEqualTo(QuestionBankGrading.NOT_PRACTICEABLE);
    }

    private static QuestionBankEntry entry(String type, String answer) {
        QuestionBankEntry entry = new QuestionBankEntry();
        entry.setEntryId("e1");
        entry.setPathId("p");
        entry.setQuestionId("q1");
        entry.setKnowledgePointId("k1");
        entry.setQuestion("prompt");
        entry.setQuestionType(type);
        entry.setCorrectAnswer(answer);
        return entry;
    }

    private static QuestionBankEntry choice(String answer) {
        QuestionBankEntry entry = entry("choice", answer);
        List<Map<String, Object>> options = new ArrayList<>();
        options.add(option("A", "first"));
        options.add(option("B", "second"));
        entry.setOptions(options);
        return entry;
    }

    private static Map<String, Object> option(String label, String body) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("label", label);
        option.put("body", body);
        return option;
    }
}
