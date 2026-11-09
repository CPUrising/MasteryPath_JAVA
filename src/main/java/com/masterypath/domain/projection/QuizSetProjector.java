package com.masterypath.domain.projection;

import com.masterypath.domain.model.PendingOption;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.domain.model.QuizItem;
import com.masterypath.domain.model.QuizSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 题卷的 learner-visible 投影（{@code 0033}）。
 *
 * <p>提交前只暴露题干/选项/难度；{@code expected_answer}、{@code explanation}、可接受答案与
 * 关键词 MUST NOT 出现在任何公开投影中（{@code INV-07}）。提交后才释放答案与解析。
 */
public final class QuizSetProjector {

    private QuizSetProjector() {
    }

    /** 题卷元数据 + 题目列表；{@code includeAnswers=false} 时隐藏答案键。 */
    public static Map<String, Object> publicSet(QuizSet set, List<QuizItem> items,
                                                boolean includeAnswers) {
        Map<String, Object> view = new LinkedHashMap<>();
        if (set == null) {
            return view;
        }
        view.put("set_id", set.getSetId());
        view.put("path_id", set.getPathId());
        view.put("title", set.getTitle());
        view.put("topic", set.getTopic());
        view.put("status", set.getStatus());
        view.put("question_count", set.getQuestionCount());
        view.put("score", set.getScore());
        view.put("created_at", set.getCreatedAt());
        view.put("submitted_at", set.getSubmittedAt());
        List<Map<String, Object>> questions = new ArrayList<>();
        for (QuizItem item : items == null ? List.<QuizItem>of() : items) {
            questions.add(includeAnswers ? submittedItem(item) : publicItem(item));
        }
        view.put("questions", questions);
        return view;
    }

    /** 单题公开投影：只有题干、选项与难度。 */
    public static Map<String, Object> publicItem(QuizItem item) {
        Map<String, Object> view = new LinkedHashMap<>();
        if (item == null || item.getQuestion() == null) {
            return view;
        }
        PendingQuestion question = item.getQuestion();
        view.put("question_id", question.getQuestionId());
        view.put("seq", item.getSeq());
        view.put("prompt", question.getPrompt());
        view.put("question_type", question.getQuestionType().value());
        view.put("options", publicOptions(question));
        view.put("difficulty", question.getDifficulty());
        return view;
    }

    /** 提交后的单题投影：追加学习者答案、判定、正确答案与解析。 */
    public static Map<String, Object> submittedItem(QuizItem item) {
        Map<String, Object> view = publicItem(item);
        if (item == null || item.getQuestion() == null) {
            return view;
        }
        PendingQuestion question = item.getQuestion();
        view.put("learner_answer", item.getUserAnswer());
        view.put("is_correct", item.isCorrect());
        view.put("result", item.getResult());
        view.put("correct_answer", question.getExpectedAnswer());
        view.put("explanation", question.getExplanation());
        return view;
    }

    private static List<Map<String, Object>> publicOptions(PendingQuestion question) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (PendingOption option : question.getOptions()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("label", option.getLabel());
            entry.put("body", option.getBody());
            options.add(entry);
        }
        return options;
    }
}
