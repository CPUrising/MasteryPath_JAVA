package com.masterypath.domain.bank;

import com.masterypath.domain.grading.Decision;
import com.masterypath.domain.grading.Grading;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.domain.model.QuestionBankEntry;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 题库条目的刷题判分适配（{@code 0046}）。
 *
 * <p><b>纯函数层</b>：零 I/O、零 Spring、零 LLM。把题库条目还原成
 * {@link PendingQuestion} 后复用 {@link Grading}，不复制任何判分规则。
 *
 * <p>可刷题限定为 {@code choice} / {@code short}：题库不保存 {@code open} 题的关键词，
 * {@code written} 是定性证据而非题目，二者都无法确定性判分，一律排除（INV-33 / INV-46）。
 */
public final class QuestionBankGrading {

    /** 不可刷题时的统一原因。 */
    public static final String NOT_PRACTICEABLE = "question is not practiceable";

    private QuestionBankGrading() {
    }

    /** 题库条目是否可确定性判分。 */
    public static boolean practiceable(QuestionBankEntry entry) {
        if (entry == null) {
            return false;
        }
        String type = entry.getQuestionType() == null
                ? "" : entry.getQuestionType().trim().toLowerCase(Locale.ROOT);
        String answer = entry.getCorrectAnswer() == null ? "" : entry.getCorrectAnswer().trim();
        if (answer.isEmpty()) {
            return false;
        }
        if ("short".equals(type)) {
            return true;
        }
        if (!"choice".equals(type)) {
            return false;
        }
        Map<String, String> options = optionMap(entry);
        if (options.isEmpty()) {
            return false;
        }
        // 答案必须能落到某个选项上，否则选择题永远判错。
        return options.containsKey(answer.toUpperCase(Locale.ROOT));
    }

    /**
     * 按题库条目判分；不可刷时返回 {@code ungraded}，绝不猜。
     */
    public static Decision grade(QuestionBankEntry entry, String answer) {
        if (!practiceable(entry)) {
            return Decision.ungraded(NOT_PRACTICEABLE);
        }
        return Grading.grade(toPending(entry), answer);
    }

    private static PendingQuestion toPending(QuestionBankEntry entry) {
        PendingQuestion pending = new PendingQuestion();
        String questionId = entry.getQuestionId() == null ? "" : entry.getQuestionId();
        pending.setQuestionId(questionId.isEmpty() ? entry.getEntryId() : questionId);
        pending.setKnowledgePointId(entry.getKnowledgePointId());
        pending.setModuleId("");
        pending.setPrompt(entry.getQuestion());
        pending.setQuestionType(QuestionTypeParser.parse(entry.getQuestionType()));
        pending.setExpectedAnswer(entry.getCorrectAnswer());
        pending.setOptions(entry.getOptions());
        pending.setExplanation(entry.getExplanation());
        pending.setDifficulty(entry.getDifficulty());
        return pending;
    }

    /** 题库选项（{@code label/body}）转成 {@code {label: body}}，缺失键跳过。 */
    private static Map<String, String> optionMap(QuestionBankEntry entry) {
        Map<String, String> options = new LinkedHashMap<>();
        for (Map<String, Object> option : entry.getOptions()) {
            Object label = option.get("label");
            Object body = option.get("body");
            if (label == null || String.valueOf(label).isBlank()) {
                continue;
            }
            options.put(String.valueOf(label), body == null ? "" : String.valueOf(body));
        }
        return options;
    }

    /** 题型解析：只认 choice / short，其余回退 {@code SHORT}（调用前已被 {@link #practiceable} 拦截）。 */
    private static final class QuestionTypeParser {
        private QuestionTypeParser() {
        }

        static PendingQuestion.QuestionType parse(String raw) {
            String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            if ("choice".equals(value)) {
                return PendingQuestion.QuestionType.CHOICE;
            }
            return PendingQuestion.QuestionType.SHORT;
        }
    }
}
