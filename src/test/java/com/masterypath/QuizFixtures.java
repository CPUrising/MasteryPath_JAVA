package com.masterypath;

import com.masterypath.domain.model.PendingOption;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.domain.model.QuizItem;
import com.masterypath.domain.model.QuizSet;
import com.masterypath.domain.model.TimeSupport;

import java.util.ArrayList;
import java.util.List;

/** 题卷测试夹具：合成题目，不依赖真实模型。 */
public final class QuizFixtures {

    private QuizFixtures() {
    }

    public static PendingQuestion choice(String questionId, String expectedLabel) {
        PendingQuestion question = new PendingQuestion();
        question.setQuestionId(questionId);
        question.setPrompt("Pick one for " + questionId);
        question.setQuestionType(PendingQuestion.QuestionType.CHOICE);
        question.setExpectedAnswer(expectedLabel);
        question.setOptions(List.of(
                new PendingOption("A", "first option"),
                new PendingOption("B", "second option")));
        question.setExplanation("because " + expectedLabel);
        question.setDifficulty("easy");
        return question;
    }

    public static PendingQuestion shortAnswer(String questionId, String expected,
                                              String... accepted) {
        PendingQuestion question = new PendingQuestion();
        question.setQuestionId(questionId);
        question.setPrompt("Answer " + questionId);
        question.setQuestionType(PendingQuestion.QuestionType.SHORT);
        question.setExpectedAnswer(expected);
        question.setAcceptedAnswers(new ArrayList<>(List.of(accepted)));
        question.setExplanation("expected " + expected);
        question.setDifficulty("easy");
        return question;
    }

    public static QuizItem item(String setId, int seq, PendingQuestion question) {
        QuizItem item = new QuizItem();
        item.setItemId("item-" + seq);
        item.setSetId(setId);
        item.setSeq(seq);
        item.setQuestion(question);
        item.setUpdatedAt(TimeSupport.nowSeconds());
        return item;
    }

    public static QuizSet set(String setId, String pathId, int count) {
        QuizSet set = new QuizSet();
        set.setSetId(setId);
        set.setPathId(pathId);
        set.setTitle("Practice");
        set.setTopic("caching");
        set.setQuestionCount(count);
        set.setCreatedAt(TimeSupport.nowSeconds());
        return set;
    }
}
