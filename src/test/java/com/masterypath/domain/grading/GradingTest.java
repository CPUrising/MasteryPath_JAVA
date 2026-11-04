package com.masterypath.domain.grading;

import com.masterypath.domain.model.PendingOption;
import com.masterypath.domain.model.PendingQuestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 确定性判分（{@code TC-H1}…{@code TC-H5}）。
 *
 * <p>核心：无法解析的作答 MUST 返回 {@code ungraded} 而非 {@code incorrect}。
 */
class GradingTest {

    private static PendingQuestion choice(String expected, String... bodies) {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q");
        pending.setKnowledgePointId("k");
        pending.setQuestionType(PendingQuestion.QuestionType.CHOICE);
        pending.setExpectedAnswer(expected);
        List<PendingOption> options = new java.util.ArrayList<>();
        for (int index = 0; index < bodies.length; index++) {
            options.add(new PendingOption(String.valueOf((char) ('A' + index)), bodies[index]));
        }
        pending.setOptions(options);
        return pending;
    }

    private static PendingQuestion shortAnswer(String expected) {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q");
        pending.setKnowledgePointId("k");
        pending.setQuestionType(PendingQuestion.QuestionType.SHORT);
        pending.setExpectedAnswer(expected);
        return pending;
    }

    // -- TC-H1 ----------------------------------------------------------------

    @Test
    @DisplayName("TC-H1：写选项正文而非标签，正文唯一匹配即判对")
    void choiceBodyUniqueMatch() {
        assertThat(Grading.grade(choice("B", "3", "4", "5"), "4").result())
                .isEqualTo(Decision.CORRECT);
    }

    @Test
    @DisplayName("TC-H1：直接写标签也判对")
    void choiceLabelMatches() {
        assertThat(Grading.grade(choice("B", "3", "4", "5"), "B").result())
                .isEqualTo(Decision.CORRECT);
    }

    @Test
    @DisplayName("TC-H1：小写标签同样命中")
    void choiceLowercaseLabel() {
        assertThat(Grading.grade(choice("B", "3", "4", "5"), "b").result())
                .isEqualTo(Decision.CORRECT);
    }

    @Test
    @DisplayName("TC-H1：选错标签判错并带 resolved")
    void choiceWrongLabel() {
        Decision decision = Grading.grade(choice("B", "3", "4", "5"), "A");

        assertThat(decision.result()).isEqualTo(Decision.INCORRECT);
        assertThat(decision.resolved()).isEqualTo("A");
    }

    // -- TC-H2 ----------------------------------------------------------------

    @Test
    @DisplayName("TC-H2：无法解析的选项返回 ungraded（不是 incorrect）")
    void choiceUnparseableIsUngraded() {
        Decision decision = Grading.grade(choice("B", "3", "4", "5"), "zzz");

        assertThat(decision.result()).isEqualTo(Decision.UNGRADED);
        assertThat(decision.reason()).contains("could not tell which option");
    }

    @Test
    @DisplayName("TC-H2：正文重复导致歧义 → ungraded")
    void choiceAmbiguousBodyIsUngraded() {
        Decision decision = Grading.grade(choice("A", "same", "same"), "same");

        assertThat(decision.result()).isEqualTo(Decision.UNGRADED);
    }

    @Test
    @DisplayName("TC-H2：空作答 → ungraded")
    void emptyChoiceAnswerIsUngraded() {
        assertThat(Grading.grade(choice("B", "3", "4"), "").result())
                .isEqualTo(Decision.UNGRADED);
    }

    // -- TC-H3 ----------------------------------------------------------------

    @Test
    @DisplayName("TC-H3：2 对 2.0 判对，2 对 3 判错")
    void shortNumeric() {
        PendingQuestion pending = shortAnswer("2");

        assertThat(Grading.grade(pending, "2.0").result()).isEqualTo(Decision.CORRECT);
        assertThat(Grading.grade(pending, "3").result()).isEqualTo(Decision.INCORRECT);
    }

    @Test
    @DisplayName("TC-H3：数字等价用相对容差")
    void numericTolerance() {
        assertThat(Grading.grade(shortAnswer("0.1"), "0.10000000001").result())
                .isEqualTo(Decision.CORRECT);
        assertThat(Grading.grade(shortAnswer("0.1"), "0.2").result())
                .isEqualTo(Decision.INCORRECT);
    }

    // -- TC-H4 ----------------------------------------------------------------

    @Test
    @DisplayName("TC-H4：相似度 0.90 判对，0.80 判错")
    void shortFuzzyThreshold() {
        PendingQuestion pending = shortAnswer("abcdefghij");

        assertThat(Grading.similarity("abcdefghi", "abcdefghij")).isGreaterThanOrEqualTo(0.90);
        assertThat(Grading.grade(pending, "abcdefghi").result()).isEqualTo(Decision.CORRECT);

        assertThat(Grading.similarity("abcdefgh", "abcdefghij")).isLessThan(0.90);
        assertThat(Grading.grade(pending, "abcdefgh").result()).isEqualTo(Decision.INCORRECT);
    }

    @Test
    @DisplayName("TC-H4：超过 30 字的作答只做精确匹配")
    void longAnswerOnlyExact() {
        String expected = "a".repeat(40) + "b";
        PendingQuestion pending = shortAnswer(expected);

        // 只差一个字符，相似度 > 0.9，但长度超限 → 判错
        assertThat(Grading.grade(pending, "a".repeat(40) + "c").result())
                .isEqualTo(Decision.INCORRECT);
        assertThat(Grading.grade(pending, expected).result()).isEqualTo(Decision.CORRECT);
    }

    @Test
    @DisplayName("TC-H4：可接受答案列表命中即判对")
    void acceptedAnswersMatch() {
        PendingQuestion pending = shortAnswer("four");
        pending.setAcceptedAnswers(List.of("4", "four"));

        assertThat(Grading.grade(pending, "4").result()).isEqualTo(Decision.CORRECT);
    }

    // -- TC-H5 ----------------------------------------------------------------

    @Test
    @DisplayName("TC-H5：必需词全中但可选词不足 → partial")
    void openOptionalInsufficient() {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q");
        pending.setKnowledgePointId("k");
        pending.setQuestionType(PendingQuestion.QuestionType.OPEN);
        pending.setRequiredKeywords(List.of("photosynthesis"));
        pending.setOptionalKeywords(List.of("chlorophyll", "sunlight", "water", "carbon dioxide"));

        assertThat(Grading.grade(pending, "photosynthesis uses chlorophyll").result())
                .isEqualTo(Decision.PARTIAL);
    }

    @Test
    @DisplayName("TC-H5：必需词全中且可选词达标 → correct（服务层再降级）")
    void openAllKeywords() {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q");
        pending.setKnowledgePointId("k");
        pending.setQuestionType(PendingQuestion.QuestionType.OPEN);
        pending.setRequiredKeywords(List.of("photosynthesis"));
        pending.setOptionalKeywords(List.of("chlorophyll", "sunlight", "water", "carbon dioxide"));

        assertThat(Grading.grade(pending,
                "photosynthesis chlorophyll sunlight water carbon dioxide").result())
                .isEqualTo(Decision.CORRECT);
    }

    @Test
    @DisplayName("TC-H5：必需词全没中 → incorrect")
    void openMissingAllRequired() {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q");
        pending.setKnowledgePointId("k");
        pending.setQuestionType(PendingQuestion.QuestionType.OPEN);
        pending.setRequiredKeywords(List.of("photosynthesis"));

        assertThat(Grading.grade(pending, "something else").result())
                .isEqualTo(Decision.INCORRECT);
    }

    @Test
    @DisplayName("TC-H5：中了一部分必需词 → partial")
    void openPartialRequired() {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q");
        pending.setKnowledgePointId("k");
        pending.setQuestionType(PendingQuestion.QuestionType.OPEN);
        pending.setRequiredKeywords(List.of("photosynthesis", "chlorophyll"));

        assertThat(Grading.grade(pending, "photosynthesis happens").result())
                .isEqualTo(Decision.PARTIAL);
    }

    @Test
    @DisplayName("开放题无关键词 → ungraded（无法确定性判定）")
    void openNoKeywordsIsUngraded() {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q");
        pending.setKnowledgePointId("k");
        pending.setQuestionType(PendingQuestion.QuestionType.OPEN);

        Decision decision = Grading.grade(pending, "an answer");

        assertThat(decision.result()).isEqualTo(Decision.UNGRADED);
        assertThat(decision.reason()).isEqualTo("no keywords registered");
    }

    @Test
    @DisplayName("开放题空作答 → ungraded")
    void openEmptyIsUngraded() {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q");
        pending.setKnowledgePointId("k");
        pending.setQuestionType(PendingQuestion.QuestionType.OPEN);
        pending.setRequiredKeywords(List.of("x"));

        assertThat(Grading.grade(pending, "   ").result()).isEqualTo(Decision.UNGRADED);
    }

    // -- normalize ------------------------------------------------------------

    @Test
    @DisplayName("normalize：折叠空白并剥离首尾标点")
    void normalizeStripsPunctuation() {
        assertThat(Grading.normalize("  Hello   World!  ")).isEqualTo("hello world");
        assertThat(Grading.normalize("（答案）")).isEqualTo("答案");
    }

    @Test
    @DisplayName("normalize：含 CJK 时删除所有空格")
    void normalizeRemovesSpacesForCjk() {
        assertThat(Grading.normalize("光 合 作 用")).isEqualTo("光合作用");
    }

    @Test
    @DisplayName("normalize：全角数字归一为半角")
    void normalizeFullWidthDigits() {
        assertThat(Grading.normalize("２+２")).isEqualTo("2+2");
    }

    @Test
    @DisplayName("normalize：null 与空串都返回空串")
    void normalizeNullSafe() {
        assertThat(Grading.normalize(null)).isEmpty();
        assertThat(Grading.normalize("")).isEmpty();
    }

    // -- similarity -----------------------------------------------------------

    @Test
    @DisplayName("similarity：完全相同为 1.0，完全不同为 0.0")
    void similarityBounds() {
        assertThat(Grading.similarity("abc", "abc")).isEqualTo(1.0);
        assertThat(Grading.similarity("abc", "xyz")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("similarity：两者皆空为 1.0")
    void similarityEmptyBoth() {
        assertThat(Grading.similarity("", "")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("similarity：单字符差异按最长长度归一")
    void similaritySingleDifference() {
        assertThat(Grading.similarity("abcd", "abc")).isEqualTo(0.75);
    }

    // -- resolveChoice --------------------------------------------------------

    @Test
    @DisplayName("resolveChoice：标签优先于正文")
    void resolveChoicePrefersLabel() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("A", "B");
        options.put("B", "4");

        assertThat(Grading.resolveChoice("B", options)).isEqualTo("B");
    }

    @Test
    @DisplayName("resolveChoice：未命中返回空串")
    void resolveChoiceMissReturnsEmpty() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("A", "3");
        options.put("B", "4");

        assertThat(Grading.resolveChoice("zzz", options)).isEmpty();
        assertThat(Grading.resolveChoice("", options)).isEmpty();
    }
}
