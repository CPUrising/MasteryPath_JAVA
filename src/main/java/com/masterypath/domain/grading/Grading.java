package com.masterypath.domain.grading;

import com.masterypath.domain.model.PendingOption;
import com.masterypath.domain.model.PendingQuestion;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 确定性判分（{@code ADR-008} / spec 06 §七）。
 *
 * <p><b>纯函数层</b>：零 I/O、零 LLM。全部参照物（期望答案、选项、关键词、可接受答案）
 * 在<b>注册时</b>就已固定，因此判分不需要模型。
 *
 * <p>最重要的规则：<b>无法解析的作答永不判错</b>——返回 {@code ungraded}，
 * 使猜测不被记成负面（{@code INV-46}）。
 */
public final class Grading {

    /** CJK 字符范围（与参考实现 {@code [\u3400-\u9fff\uf900-\ufaff]} 一致）。 */
    private static final Pattern CJK = Pattern.compile("[\u3400-\u9fff\uf900-\ufaff]");

    /** 首尾需要剥离的标点（逐字复刻参考实现的 {@code _PUNCT}）。 */
    private static final String PUNCT = " \t\r\n。．.，,！!？?；;：:、\"'`()（）[]【】";

    /** 短答模糊匹配阈值。 */
    public static final double SHORT_THRESHOLD = 0.90;

    /** 开放题可选关键词命中率下限。 */
    public static final double OPEN_OPTIONAL_RATIO = 0.60;

    /** 超过此长度的作答只做精确匹配。 */
    private static final int FUZZY_MAX_LENGTH = 30;

    private Grading() {
    }

    // -- 归一化 ---------------------------------------------------------------

    /**
     * 归一化文本：NFKC → 小写 → trim → 折叠空白 → 剥首尾标点 → 含 CJK 则删所有空格。
     *
     * <p>步骤顺序不可调整：先剥标点再折叠空白，会让 {@code "a 。 b"} 与 {@code "a b"} 不等价。
     *
     * <p>已知差异：Python 的 {@code casefold()} 与 Java 的 {@code toLowerCase(Locale.ROOT)}
     * 对德语 {@code ß} 等少数场景不同。已登记到 {@code docs/migration-plan.md} §11。
     */
    public static String normalize(String text) {
        String value = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim();
        value = value.replaceAll("\\s+", " ");
        value = strip(value, PUNCT);
        if (CJK.matcher(value).find()) {
            value = value.replace(" ", "");
        }
        return value;
    }

    /** 剥离首尾出现在 {@code chars} 中的字符。 */
    private static String strip(String value, String chars) {
        int start = 0;
        int end = value.length();
        while (start < end && chars.indexOf(value.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && chars.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(start, end);
    }

    // -- 相似度 ---------------------------------------------------------------

    /** 编辑距离（Levenshtein）。 */
    static int levenshtein(String a, String b) {
        if (a.equals(b)) {
            return 0;
        }
        if (a.isEmpty()) {
            return b.length();
        }
        if (b.isEmpty()) {
            return a.length();
        }
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int[] current = new int[b.length() + 1];
            current[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= b.length(); j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(previous[j] + 1, current[j - 1] + 1),
                        previous[j - 1] + cost);
            }
            previous = current;
        }
        return previous[b.length()];
    }

    /** 归一化相似度：{@code 1 - distance / max(len)}；两者皆空返回 1.0。 */
    public static double similarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        int longest = Math.max(a.length(), b.length());
        if (longest == 0) {
            return 1.0;
        }
        return 1.0 - (double) levenshtein(a, b) / longest;
    }

    // -- 选择题解析 -----------------------------------------------------------

    /**
     * 把学习者作答解析为选项标签；歧义或无法识别返回空串。
     *
     * <p>先按标签（忽略大小写）直接命中，再按正文归一化后**唯一**匹配。
     */
    public static String resolveChoice(String answer, Map<String, String> options) {
        String raw = answer == null ? "" : answer.trim();
        if (!raw.isEmpty() && options.containsKey(raw.toUpperCase(Locale.ROOT))) {
            return raw.toUpperCase(Locale.ROOT);
        }
        String normalized = normalize(raw);
        if (normalized.isEmpty()) {
            return "";
        }
        List<String> matches = new ArrayList<>();
        for (Map.Entry<String, String> entry : options.entrySet()) {
            if (normalize(entry.getValue()).equals(normalized)) {
                matches.add(entry.getKey());
            }
        }
        return matches.size() == 1 ? matches.get(0) : "";
    }

    // -- 判分入口 -------------------------------------------------------------

    /**
     * 按题型判分。
     *
     * @param pending 服务端完整题目（含期望答案与关键词）
     * @param answer  学习者作答
     */
    public static Decision grade(PendingQuestion pending, String answer) {
        if (pending == null) {
            return Decision.ungraded("no question");
        }
        PendingQuestion.QuestionType type = pending.getQuestionType();
        if (type == PendingQuestion.QuestionType.CHOICE) {
            return gradeChoice(pending, answer);
        }
        if (type == PendingQuestion.QuestionType.OPEN) {
            return gradeOpen(pending, answer);
        }
        return gradeShort(pending, answer);
    }

    private static Decision gradeChoice(PendingQuestion pending, String answer) {
        Map<String, String> options = choiceMap(pending);
        String label = resolveChoice(answer, options);
        if (label.isEmpty()) {
            StringBuilder shown = new StringBuilder();
            for (Map.Entry<String, String> entry : options.entrySet()) {
                if (shown.length() > 0) {
                    shown.append("; ");
                }
                shown.append(entry.getKey()).append(" = ").append(entry.getValue());
            }
            return Decision.ungraded("could not tell which option '" + (answer == null ? "" : answer)
                    + "' picks; options: " + shown);
        }
        if (label.equals(pending.getExpectedAnswer())) {
            return new Decision(Decision.CORRECT, "", label);
        }
        return new Decision(Decision.INCORRECT, "", label);
    }

    private static Decision gradeShort(PendingQuestion pending, String answer) {
        String learner = normalize(answer);
        String target = normalize(pending.getExpectedAnswer());
        if (learner.isEmpty()) {
            return Decision.ungraded("empty answer");
        }
        if (learner.equals(target)) {
            return Decision.correct("exact");
        }
        for (String accepted : pending.getAcceptedAnswers()) {
            if (learner.equals(normalize(accepted))) {
                return Decision.correct("accepted equivalent");
            }
        }
        Double left = asNumber(learner);
        Double right = asNumber(target);
        if (left != null && right != null) {
            if (Math.abs(left - right) <= 1e-6 * Math.max(1.0, Math.abs(right))) {
                return Decision.correct("numeric");
            }
            return Decision.incorrect("numeric mismatch");
        }
        // 长作答只做精确匹配，避免模糊匹配的噪声。
        if (target.length() > FUZZY_MAX_LENGTH || learner.length() > FUZZY_MAX_LENGTH) {
            return Decision.incorrect("too long for fuzzy");
        }
        if (similarity(learner, target) >= SHORT_THRESHOLD) {
            return Decision.correct("fuzzy");
        }
        return Decision.incorrect("below fuzzy threshold");
    }

    private static Decision gradeOpen(PendingQuestion pending, String answer) {
        String text = normalize(answer);
        List<String> required = new ArrayList<>();
        for (String keyword : pending.getRequiredKeywords()) {
            if (keyword != null && !keyword.isEmpty()) {
                required.add(normalize(keyword));
            }
        }
        List<String> optional = new ArrayList<>();
        for (String keyword : pending.getOptionalKeywords()) {
            if (keyword != null && !keyword.isEmpty()) {
                optional.add(normalize(keyword));
            }
        }
        if (text.isEmpty()) {
            return Decision.ungraded("empty answer");
        }
        if (required.isEmpty() && optional.isEmpty()) {
            // 没有固定关键词 → 无法确定性判定理解程度。
            return Decision.ungraded("no keywords registered");
        }
        int requiredHits = 0;
        for (String keyword : required) {
            if (text.contains(keyword)) {
                requiredHits++;
            }
        }
        int optionalHits = 0;
        for (String keyword : optional) {
            if (text.contains(keyword)) {
                optionalHits++;
            }
        }
        if (requiredHits < required.size()) {
            return requiredHits > 0
                    ? Decision.partial("missing required")
                    : Decision.incorrect("missing required");
        }
        if (!optional.isEmpty()
                && (double) optionalHits / optional.size() < OPEN_OPTIONAL_RATIO) {
            return Decision.partial("optional keywords below ratio");
        }
        return Decision.correct("keywords satisfied");
    }

    /** {@code {label: body}}，等价于参考实现的 {@code choice_map}。 */
    static Map<String, String> choiceMap(PendingQuestion pending) {
        Map<String, String> map = new LinkedHashMap<>();
        for (PendingOption option : pending.getOptions()) {
            map.put(option.getLabel(), option.getBody());
        }
        return map;
    }

    private static Double asNumber(String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
