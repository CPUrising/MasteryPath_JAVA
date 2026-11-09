package com.masterypath.domain.quizset;

import com.masterypath.domain.quiz.QuizSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 题卷请求参数规范化（{@code 0033}）。
 *
 * <p>纯函数：题数 clamp 到 1–10；题型只保留 {@code choice/short/open}；难度只保留
 * {@code easy/medium/hard/auto}。
 */
public record QuizSetSpec(String topic, int count, String difficulty, List<String> questionTypes) {

    public static final int MIN_QUESTIONS = 1;
    public static final int MAX_QUESTIONS = 10;

    public QuizSetSpec {
        topic = topic == null || topic.strip().isEmpty() ? "practice" : topic.strip();
        count = Math.max(MIN_QUESTIONS, Math.min(MAX_QUESTIONS, count));
        String normalizedDifficulty = difficulty == null
                ? "" : difficulty.strip().toLowerCase(Locale.ROOT);
        difficulty = List.of("easy", "medium", "hard", "auto").contains(normalizedDifficulty)
                ? normalizedDifficulty : "auto";
        List<String> types = new ArrayList<>();
        if (questionTypes != null) {
            for (String type : questionTypes) {
                String normalized = type == null ? "" : type.strip().toLowerCase(Locale.ROOT);
                if (QuizSpec.QUESTION_TYPES.contains(normalized) && !types.contains(normalized)) {
                    types.add(normalized);
                }
            }
        }
        questionTypes = types.isEmpty() ? List.copyOf(QuizSpec.QUESTION_TYPES) : List.copyOf(types);
    }

    public static QuizSetSpec normalize(String topic, int count, String difficulty,
                                        List<String> questionTypes) {
        return new QuizSetSpec(topic, count, difficulty, questionTypes);
    }
}
