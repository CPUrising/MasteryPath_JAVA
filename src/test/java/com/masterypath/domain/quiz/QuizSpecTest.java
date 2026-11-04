package com.masterypath.domain.quiz;

import com.masterypath.domain.model.PendingOption;
import com.masterypath.domain.model.PendingQuestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 出题参数校验（{@code TC-K1}）。
 *
 * <p>注册时固定全部参照物，判分时刻不再访问模型（{@code INV-35}）。
 */
class QuizSpecTest {

    private static Map<String, Object> params(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            map.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return map;
    }

    private static Map<String, Object> option(String label, String body) {
        return params("label", label, "body", body);
    }

    // -- 题型推断 -------------------------------------------------------------

    @Test
    @DisplayName("TC-K1：无选项时推断为 short")
    void infersShort() {
        QuizSpec.Normalized spec = QuizSpec.normalize(
                params("question", "?", "expected_answer", "4"));

        assertThat(spec.questionType()).isEqualTo("short");
        assertThat(spec.options()).isEmpty();
        assertThat(spec.expectedAnswer()).isEqualTo("4");
    }

    @Test
    @DisplayName("TC-K1：有选项时推断为 choice")
    void infersChoice() {
        QuizSpec.Normalized spec = QuizSpec.normalize(params(
                "expected_answer", "B",
                "options", List.of(option("A", "3"), option("B", "4"))));

        assertThat(spec.questionType()).isEqualTo("choice");
        assertThat(spec.options()).hasSize(2);
        assertThat(spec.expectedAnswer()).isEqualTo("B");
    }

    @Test
    @DisplayName("TC-K1：非法题型被拒绝，文案列出全部合法值")
    void rejectsUnknownType() {
        assertThatThrownBy(() -> QuizSpec.normalize(params(
                "question_type", "essay", "expected_answer", "x")))
                .isInstanceOf(QuizSpec.QuizException.class)
                .hasMessageContaining("question_type must be one of: choice, short, open");
    }

    @Test
    @DisplayName("TC-K1：缺 expected_answer 被拒绝")
    void rejectsMissingExpected() {
        assertThatThrownBy(() -> QuizSpec.normalize(params("question", "?")))
                .isInstanceOf(QuizSpec.QuizException.class)
                .hasMessageContaining("needs expected_answer");
    }

    @Test
    @DisplayName("TC-K1：简答题带选项被拒绝")
    void rejectsOptionsOnShort() {
        assertThatThrownBy(() -> QuizSpec.normalize(params(
                "question_type", "short",
                "options", List.of(option("A", "x")),
                "expected_answer", "x")))
                .isInstanceOf(QuizSpec.QuizException.class)
                .hasMessageContaining("options cannot be used");
    }

    @Test
    @DisplayName("TC-K1：开放题带选项被拒绝")
    void rejectsOptionsOnOpen() {
        assertThatThrownBy(() -> QuizSpec.normalize(params(
                "question_type", "open",
                "options", List.of(option("A", "x")),
                "expected_answer", "x")))
                .isInstanceOf(QuizSpec.QuizException.class)
                .hasMessageContaining("options cannot be used");
    }

    // -- 选项校验 -------------------------------------------------------------

    @Test
    @DisplayName("TC-K1：标签不连续被拒绝")
    void rejectsNonSequentialLabels() {
        assertThatThrownBy(() -> QuizSpec.normalize(params(
                "expected_answer", "A",
                "options", List.of(option("A", "x"), option("C", "y")))))
                .isInstanceOf(QuizSpec.QuizException.class)
                .hasMessageContaining("choice labels must run A, B, C");
    }

    @Test
    @DisplayName("TC-K1：标签重复被拒绝")
    void rejectsDuplicateLabels() {
        assertThatThrownBy(() -> QuizSpec.normalize(params(
                "expected_answer", "A",
                "options", List.of(option("A", "x"), option("A", "y")))))
                .isInstanceOf(QuizSpec.QuizException.class)
                .hasMessageContaining("choice labels must run A, B, C");
    }

    @Test
    @DisplayName("TC-K1：选项正文重复被拒绝")
    void rejectsDuplicateBodies() {
        assertThatThrownBy(() -> QuizSpec.normalize(params(
                "expected_answer", "A",
                "options", List.of(option("A", "same"), option("B", "same")))))
                .isInstanceOf(QuizSpec.QuizException.class)
                .hasMessageContaining("bodies must be unique and non-empty");
    }

    @Test
    @DisplayName("TC-K1：选项正文为空被拒绝")
    void rejectsEmptyBody() {
        assertThatThrownBy(() -> QuizSpec.normalize(params(
                "expected_answer", "A",
                "options", List.of(option("A", ""), option("B", "y")))))
                .isInstanceOf(QuizSpec.QuizException.class)
                .hasMessageContaining("bodies must be unique and non-empty");
    }

    @Test
    @DisplayName("TC-K1：期望答案无法解析为标签被拒绝")
    void rejectsUnresolvableExpected() {
        assertThatThrownBy(() -> QuizSpec.normalize(params(
                "expected_answer", "nope",
                "options", List.of(option("A", "3"), option("B", "4")))))
                .isInstanceOf(QuizSpec.QuizException.class)
                .hasMessageContaining("must resolve to one option label");
    }

    @Test
    @DisplayName("TC-K1：期望答案写正文可被解析为标签")
    void resolvesExpectedFromBody() {
        QuizSpec.Normalized spec = QuizSpec.normalize(params(
                "expected_answer", "4",
                "options", List.of(option("A", "3"), option("B", "4"))));

        assertThat(spec.expectedAnswer()).isEqualTo("B");
    }

    @Test
    @DisplayName("TC-K1：选项标签统一转大写")
    void uppercasesLabels() {
        QuizSpec.Normalized spec = QuizSpec.normalize(params(
                "expected_answer", "b",
                "options", List.of(option("a", "3"), option("b", "4"))));

        assertThat(spec.options()).extracting(PendingOption::getLabel).containsExactly("A", "B");
        assertThat(spec.expectedAnswer()).isEqualTo("B");
    }

    @Test
    @DisplayName("TC-K1：旧式字符串选项被解析")
    void parsesLegacyStringOptions() {
        QuizSpec.Normalized spec = QuizSpec.normalize(params(
                "expected_answer", "B",
                "options", List.of("A: 3", "B: 4")));

        assertThat(spec.options()).hasSize(2);
        assertThat(spec.expectedAnswer()).isEqualTo("B");
    }

    @Test
    @DisplayName("TC-K1：options 非数组被拒绝")
    void rejectsNonArrayOptions() {
        assertThatThrownBy(() -> QuizSpec.normalize(params(
                "expected_answer", "A", "options", "not-a-list")))
                .isInstanceOf(QuizSpec.QuizException.class)
                .hasMessageContaining("options must be an array");
    }

    // -- 关键词 ---------------------------------------------------------------

    @Test
    @DisplayName("开放题关键词原样保留")
    void keepsOpenKeywords() {
        QuizSpec.Normalized spec = QuizSpec.normalize(params(
                "question_type", "open",
                "expected_answer", "x",
                "required_keywords", List.of("a", "b"),
                "optional_keywords", List.of("c")));

        assertThat(spec.requiredKeywords()).containsExactly("a", "b");
        assertThat(spec.optionalKeywords()).containsExactly("c");
    }

    @Test
    @DisplayName("开放题无关键词时用 expected_answer 兜底")
    void openKeywordsFallBackToExpected() {
        List<String> required = QuizSpec.openRequiredKeywords("open", List.of(), "photosynthesis");

        assertThat(required).containsExactly("photosynthesis");
    }

    @Test
    @DisplayName("非开放题不做关键词兜底")
    void nonOpenDoesNotFallBack() {
        assertThat(QuizSpec.openRequiredKeywords("short", List.of(), "x")).isEmpty();
    }

    // -- toPending ------------------------------------------------------------

    @Test
    @DisplayName("toPending 补齐 id、类型与关键词")
    void toPendingFillsFields() {
        QuizSpec.Normalized spec = QuizSpec.normalize(params(
                "question_type", "open",
                "expected_answer", "photosynthesis",
                "required_keywords", List.of("photosynthesis")));

        PendingQuestion pending = QuizSpec.toPending(spec, "q1", "k1", "m1",
                "explain it", "because", "easy", List.of());

        assertThat(pending.getQuestionId()).isEqualTo("q1");
        assertThat(pending.getKnowledgePointId()).isEqualTo("k1");
        assertThat(pending.getModuleId()).isEqualTo("m1");
        assertThat(pending.getPrompt()).isEqualTo("explain it");
        assertThat(pending.getQuestionType()).isEqualTo(PendingQuestion.QuestionType.OPEN);
        assertThat(pending.getDifficulty()).isEqualTo("easy");
        assertThat(pending.getRequiredKeywords()).containsExactly("photosynthesis");
    }

    @Test
    @DisplayName("canonicalLabels 生成 A、B、C")
    void canonicalLabelsSequence() {
        assertThat(QuizSpec.canonicalLabels(3)).containsExactly("A", "B", "C");
    }
}
