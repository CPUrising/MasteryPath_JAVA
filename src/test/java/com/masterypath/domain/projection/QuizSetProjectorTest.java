package com.masterypath.domain.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.masterypath.QuizFixtures;
import com.masterypath.domain.model.QuizItem;
import com.masterypath.domain.model.QuizSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class QuizSetProjectorTest {

    @Test
    @DisplayName("提交前投影不含答案键（INV-07）")
    void publicProjectionHidesAnswers() {
        QuizSet set = QuizFixtures.set("s1", "p", 2);
        List<QuizItem> items = List.of(
                QuizFixtures.item("s1", 1, QuizFixtures.choice("q1", "A")),
                QuizFixtures.item("s1", 2, QuizFixtures.shortAnswer("q2", "4", "4")));

        Map<String, Object> view = QuizSetProjector.publicSet(set, items, false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions =
                (List<Map<String, Object>>) view.get("questions");
        assertThat(questions).hasSize(2);
        for (Map<String, Object> question : questions) {
            assertThat(question).doesNotContainKeys(
                    "expected_answer", "explanation", "accepted_answers",
                    "required_keywords", "optional_keywords");
            assertThat(question).containsKeys("question_id", "prompt", "question_type", "options");
        }
    }

    @Test
    @DisplayName("提交后投影释放答案与解析")
    void submittedProjectionReleasesAnswers() {
        QuizSet set = QuizFixtures.set("s1", "p", 1);
        set.setStatus(QuizSet.STATUS_SUBMITTED);
        QuizItem item = QuizFixtures.item("s1", 1, QuizFixtures.choice("q1", "A"));
        item.setUserAnswer("A");
        item.setCorrect(true);
        item.setResult("correct");

        Map<String, Object> view = QuizSetProjector.publicSet(set, List.of(item), true);

        @SuppressWarnings("unchecked")
        Map<String, Object> question =
                ((List<Map<String, Object>>) view.get("questions")).get(0);
        assertThat(question).containsEntry("learner_answer", "A")
                .containsEntry("is_correct", true)
                .containsEntry("result", "correct")
                .containsEntry("correct_answer", "A")
                .containsEntry("explanation", "because A");
    }
}
