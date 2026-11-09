package com.masterypath.domain.quizset;

import static org.assertj.core.api.Assertions.assertThat;

import com.masterypath.QuizFixtures;
import com.masterypath.domain.grading.Decision;
import com.masterypath.domain.model.QuizItem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuizSetGraderTest {

    @Test
    @DisplayName("选择题按标签判分")
    void gradesChoiceByLabel() {
        QuizItem item = QuizFixtures.item("s1", 1, QuizFixtures.choice("q1", "A"));
        assertThat(QuizSetGrader.grade(item, "A").result()).isEqualTo(Decision.CORRECT);
        assertThat(QuizSetGrader.grade(item, "B").result()).isEqualTo(Decision.INCORRECT);
    }

    @Test
    @DisplayName("简答题接受可接受答案；无法解析返回 ungraded")
    void gradesShortAndUngraded() {
        QuizItem item = QuizFixtures.item("s1", 1,
                QuizFixtures.shortAnswer("q2", "the process of photosynthesis", "photosynthesis"));
        assertThat(QuizSetGrader.grade(item, "photosynthesis").result()).isEqualTo(Decision.CORRECT);
        assertThat(QuizSetGrader.grade(item, "").isUngraded()).isTrue();
    }

    @Test
    @DisplayName("练习卷的 open correct 不降级为 partial")
    void openCorrectStaysCorrect() {
        var question = QuizFixtures.shortAnswer("q3", "cache", "cache");
        question.setQuestionType(com.masterypath.domain.model.PendingQuestion.QuestionType.OPEN);
        question.setRequiredKeywords(java.util.List.of("cache"));
        QuizItem item = QuizFixtures.item("s1", 1, question);

        assertThat(QuizSetGrader.grade(item, "cache").result()).isEqualTo(Decision.CORRECT);
    }
}
