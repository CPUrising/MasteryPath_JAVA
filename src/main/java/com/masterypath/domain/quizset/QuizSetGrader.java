package com.masterypath.domain.quizset;

import com.masterypath.domain.grading.Decision;
import com.masterypath.domain.grading.Grading;
import com.masterypath.domain.model.QuizItem;

/**
 * 练习题卷的确定性判分（{@code 0033}）。
 *
 * <p>复用掌握题的 {@link Grading}，但练习卷不授予掌握，因此不做 open 的
 * “correct 降级为 partial”处理——{@code correct} 就是 {@code correct}。
 */
public final class QuizSetGrader {

    private QuizSetGrader() {
    }

    public static Decision grade(QuizItem item, String answer) {
        if (item == null || item.getQuestion() == null) {
            return Decision.ungraded("no question");
        }
        return Grading.grade(item.getQuestion(), answer);
    }
}
