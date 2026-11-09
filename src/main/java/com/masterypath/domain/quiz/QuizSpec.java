package com.masterypath.domain.quiz;

import com.masterypath.domain.grading.Grading;
import com.masterypath.domain.model.PendingOption;
import com.masterypath.domain.model.PendingQuestion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 出题参数校验与规范化（{@code TC-K1} / spec 06 §五）。
 *
 * <p><b>纯函数层</b>：零 I/O。注册时必须把全部参照物固定下来（{@code INV-35}），
 * 判分时刻不再访问模型。
 *
 * <p>校验要点：
 * <ul>
 *   <li>题型未给时按「有选项即 choice，否则 short」推断；</li>
 *   <li>{@code expected_answer} 必填；</li>
 *   <li>非 choice 题型 MUST NOT 带选项；</li>
 *   <li>choice 的标签必须 A、B、C… 连续且唯一，正文非空且去重；</li>
 *   <li>choice 的 {@code expected_answer} 必须能解析为某个选项标签。</li>
 * </ul>
 */
public final class QuizSpec {

    /** 允许的题型（顺序即错误提示顺序）。 */
    public static final List<String> QUESTION_TYPES = List.of("choice", "short", "open");

    /** 允许的难度。 */
    public static final List<String> DIFFICULTIES = List.of("easy", "medium", "hard");

    private QuizSpec() {
    }

    /**
     * 规范化后的出题参数。
     *
     * @param questionType     {@code choice} / {@code short} / {@code open}
     * @param options          规范化后的选项（非 choice 时为空）
     * @param expectedAnswer   解析后的期望答案（choice 时为选项标签）
     * @param requiredKeywords 开放题必需关键词
     * @param optionalKeywords 开放题可选关键词
     */
    public record Normalized(
            String questionType,
            List<PendingOption> options,
            String expectedAnswer,
            List<String> requiredKeywords,
            List<String> optionalKeywords) {
    }

    /**
     * 校验并规范化出题参数。
     *
     * @param params 模型给出的原始参数
     * @throws QuizException 参数非法
     */
    public static Normalized normalize(Map<String, Object> params) {
        Map<String, Object> safe = params == null ? Map.of() : params;
        List<PendingOption> options = readOptions(safe.get("options"));
        String supplied = str(safe.get("question_type")).trim().toLowerCase(Locale.ROOT);
        if (!supplied.isEmpty() && !QUESTION_TYPES.contains(supplied)) {
            throw new QuizException("question_type must be one of: "
                    + String.join(", ", QUESTION_TYPES));
        }
        String questionType = supplied.isEmpty() ? (options.isEmpty() ? "short" : "choice") : supplied;
        String expected = str(safe.get("expected_answer")).trim();
        if (expected.isEmpty()) {
            throw new QuizException("mastery_quiz needs expected_answer");
        }

        if (!"choice".equals(questionType)) {
            if (!options.isEmpty()) {
                throw new QuizException(
                        "options cannot be used with question_type='" + questionType + "'");
            }
            List<String> required = readKeywords(safe.get("required_keywords"));
            List<String> optional = readKeywords(safe.get("optional_keywords"));
            return new Normalized(questionType, List.of(), expected, required, optional);
        }

        List<String> labels = new ArrayList<>();
        for (PendingOption option : options) {
            labels.add(option.getLabel().toUpperCase(Locale.ROOT));
        }
        if (options.isEmpty() || !new LinkedHashSet<>(labels).equals(new LinkedHashSet<>(canonicalLabels(options.size())))) {
            throw new QuizException("choice labels must run A, B, C… with one option each");
        }

        List<String> bodies = new ArrayList<>();
        for (PendingOption option : options) {
            String body = String.join(" ", option.getBody().trim().split("\\s+"))
                    .toLowerCase(Locale.ROOT);
            bodies.add(body);
        }
        boolean anyEmpty = bodies.stream().anyMatch(String::isEmpty);
        if (anyEmpty || new LinkedHashSet<>(bodies).size() != bodies.size()) {
            throw new QuizException("choice option bodies must be unique and non-empty");
        }

        Map<String, String> choiceMap = new LinkedHashMap<>();
        List<PendingOption> normalized = new ArrayList<>();
        for (PendingOption option : options) {
            String label = option.getLabel().toUpperCase(Locale.ROOT);
            choiceMap.put(label, option.getBody());
            normalized.add(new PendingOption(label, option.getBody()));
        }
        String resolved = Grading.resolveChoice(expected, choiceMap);
        if (resolved.isEmpty()) {
            throw new QuizException("choice expected_answer must resolve to one option label");
        }
        return new Normalized("choice", normalized, resolved, List.of(), List.of());
    }

    /**
     * 开放题的关键词兜底：未提供关键词时用期望答案作为唯一必需词。
     *
     * <p>只对 open 生效；其他题型返回空列表。
     */
    public static List<String> openRequiredKeywords(String questionType,
                                                    List<String> required,
                                                    String expectedAnswer) {
        if (!"open".equals(questionType)) {
            return List.of();
        }
        if (required != null && !required.isEmpty()) {
            return required;
        }
        return expectedAnswer == null || expectedAnswer.isEmpty()
                ? List.of()
                : List.of(expectedAnswer);
    }

    /**
     * short 题是否可仅靠期望答案判定。
     *
     * <p>纯数值与 ASCII 单 token（如 {@code 42}、{@code HTTP}、{@code A/B}）没有同义表述问题；
     * 自然语言短语建议提供 {@code accepted_answers}，否则等价作答容易被判错。
     */
    public static boolean isSelfGradingShort(String expectedAnswer) {
        String value = expectedAnswer == null ? "" : expectedAnswer.trim();
        if (value.isEmpty()) {
            return true;
        }
        if (value.matches("[+-]?\\d+(?:\\.\\d+)?")) {
            return true;
        }
        return value.matches("[A-Za-z0-9_./:+-]+") && !value.contains(" ");
    }

    /** 构造服务端完整题目（补齐 id、类型、关键词）。 */
    public static PendingQuestion toPending(Normalized spec,
                                           String questionId,
                                           String knowledgePointId,
                                           String moduleId,
                                           String prompt,
                                           String explanation,
                                           String difficulty,
                                           List<String> acceptedAnswers) {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId(questionId);
        pending.setKnowledgePointId(knowledgePointId);
        pending.setModuleId(moduleId);
        pending.setPrompt(prompt);
        pending.setQuestionType(PendingQuestion.QuestionType.fromValue(spec.questionType()));
        pending.setExpectedAnswer(spec.expectedAnswer());
        pending.setOptions(spec.options());
        pending.setExplanation(explanation);
        pending.setDifficulty(difficulty);
        pending.setAcceptedAnswers(acceptedAnswers);
        pending.setRequiredKeywords(spec.requiredKeywords());
        pending.setOptionalKeywords(spec.optionalKeywords());
        return pending;
    }

    // -- 内部 ----------------------------------------------------------------

    /** {@code A, B, C…} */
    static List<String> canonicalLabels(int count) {
        List<String> labels = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            labels.add(String.valueOf((char) ('A' + index)));
        }
        return labels;
    }

    /** 读取选项：接受 {@code {label, body}} 对象或 {@code "A: body"} 旧式字符串。 */
    @SuppressWarnings("unchecked")
    static List<PendingOption> readOptions(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new QuizException("options must be an array");
        }
        if (list.isEmpty()) {
            return List.of();
        }
        boolean allMaps = list.stream().allMatch(item -> item instanceof Map<?, ?>);
        boolean allStrings = list.stream().allMatch(item -> item instanceof String);
        if (allMaps) {
            List<PendingOption> options = new ArrayList<>();
            for (Object item : list) {
                Map<?, ?> map = (Map<?, ?>) item;
                options.add(new PendingOption(
                        str(map.get("label")),
                        str(map.get("body"))));
            }
            return options;
        }
        if (allStrings) {
            // 复用 PendingQuestion 的旧式选项解析（同一套规则，避免重复实现）。
            PendingQuestion holder = new PendingQuestion();
            holder.setOptions(new ArrayList<>(list));
            return new ArrayList<>(holder.getOptions());
        }
        throw new QuizException("options must be {label, body} objects or 'A: body' strings");
    }

    private static List<String> readKeywords(Object raw) {
        List<String> keywords = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                keywords.add(str(item));
            }
        }
        return keywords;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** 出题参数非法（对应参考实现抛出的 {@code OutlineError}）。 */
    public static class QuizException extends IllegalArgumentException {
        public QuizException(String message) {
            super(message);
        }
    }
}
