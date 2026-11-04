package com.masterypath.domain.projection;

import com.masterypath.domain.model.PendingOption;
import com.masterypath.domain.model.PendingQuestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 学习者可见投影（{@code TC-E4} / {@code INV-07}）。
 */
class QuestionCardProjectorTest {

    private static PendingQuestion question() {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q1");
        pending.setKnowledgePointId("k1");
        pending.setModuleId("m1");
        pending.setPrompt("2+2?");
        pending.setQuestionType(PendingQuestion.QuestionType.CHOICE);
        pending.setExpectedAnswer("4");
        pending.setExplanation("because arithmetic");
        pending.setDifficulty("easy");
        pending.setAcceptedAnswers(List.of("4", "four"));
        pending.setRequiredKeywords(List.of("four"));
        pending.setOptionalKeywords(List.of("sum"));
        pending.setOptions(List.of(
                new PendingOption("A", "3"),
                new PendingOption("B", "4")));
        return pending;
    }

    @Test
    @DisplayName("TC-E4：投影不含期望答案与解析")
    void hidesAnswerAndExplanation() {
        Map<String, Object> publicView = QuestionCardProjector.publicPendingQuestion(question());

        assertThat(publicView).doesNotContainKeys("expected_answer", "explanation");
        assertThat(publicView.get("prompt")).isEqualTo("2+2?");
    }

    @Test
    @DisplayName("TC-E4：投影恰好 7 个键（白名单，不是黑名单）")
    void projectionHasExactlySevenKeys() {
        Map<String, Object> publicView = QuestionCardProjector.publicPendingQuestion(question());

        assertThat(publicView).containsOnlyKeys(
                "question_id", "knowledge_point_id", "module_id",
                "prompt", "question_type", "options", "difficulty");
    }

    @Test
    @DisplayName("投影不泄漏开放题关键词与内部时间戳")
    void hidesKeywordsAndTimestamp() {
        Map<String, Object> publicView = QuestionCardProjector.publicPendingQuestion(question());

        assertThat(publicView).doesNotContainKeys(
                "accepted_answers", "required_keywords", "optional_keywords", "created_at");
    }

    @Test
    @DisplayName("投影字段值逐项正确")
    void fieldValuesMatch() {
        Map<String, Object> publicView = QuestionCardProjector.publicPendingQuestion(question());

        assertThat(publicView.get("question_id")).isEqualTo("q1");
        assertThat(publicView.get("knowledge_point_id")).isEqualTo("k1");
        assertThat(publicView.get("module_id")).isEqualTo("m1");
        assertThat(publicView.get("question_type")).isEqualTo("choice");
        assertThat(publicView.get("difficulty")).isEqualTo("easy");
    }

    @Test
    @DisplayName("选项只投影 label 与 body")
    @SuppressWarnings("unchecked")
    void optionsKeepOnlyLabelAndBody() {
        Map<String, Object> publicView = QuestionCardProjector.publicPendingQuestion(question());
        List<Map<String, Object>> options = (List<Map<String, Object>>) publicView.get("options");

        assertThat(options).hasSize(2);
        assertThat(options.get(0)).containsExactlyInAnyOrderEntriesOf(
                Map.of("label", "A", "body", "3"));
        assertThat(options.get(1)).containsExactlyInAnyOrderEntriesOf(
                Map.of("label", "B", "body", "4"));
    }

    @Test
    @DisplayName("null 输入返回空 map，不抛异常")
    void nullReturnsEmptyMap() {
        assertThat(QuestionCardProjector.publicPendingQuestion(null)).isEmpty();
    }

    @Test
    @DisplayName("无选项时投影为空列表而非 null")
    void noOptionsYieldsEmptyList() {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q2");
        pending.setKnowledgePointId("k1");

        assertThat(QuestionCardProjector.publicPendingQuestion(pending).get("options"))
                .isEqualTo(List.of());
    }
}
