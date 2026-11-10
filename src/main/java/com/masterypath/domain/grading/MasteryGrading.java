package com.masterypath.domain.grading;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 掌握式判分（逐字复刻 DeepTutor {@code learning/grading.py::grade_answer}）。
 *
 * <p>与 {@link Grading} 的区别：本类只返回布尔，且是 <b>fail-closed</b>——
 * 期望答案为空即判错；不识别选项正文、不做数值容差、没有 {@code partial} /
 * {@code ungraded}。只有掌握题使用本类；练习题卷继续使用 {@link Grading}。
 */
public final class MasteryGrading {

    /** 简答题模糊匹配阈值。 */
    public static final double SHORT_THRESHOLD = 0.85;

    /** 超过此长度的简答题只做精确匹配。 */
    private static final int SHORT_FUZZY_MAX_LENGTH = 30;

    /** 开放题关键词命中率下限。 */
    public static final double OPEN_KEYWORD_RATIO = 0.6;

    /** 开放题期望答案的分隔符（与 Python 的 {@code [,;，；。\n]+} 一致）。 */
    private static final String OPEN_DELIMITER = "[,;，；。\\n]+";

    private MasteryGrading() {
    }

    /**
     * 判定一次作答是否正确。
     *
     * @param userAnswer     学习者作答
     * @param expectedAnswer 服务端固定的期望答案
     * @param questionType   {@code choice} / {@code short} / {@code open}
     * @return {@code true} 仅当答案匹配
     */
    public static boolean isCorrect(String userAnswer, String expectedAnswer, String questionType) {
        String user = stripLower(userAnswer);
        String expected = stripLower(expectedAnswer);
        if (expected.isEmpty()) {
            return false;
        }
        String type = questionType == null ? "" : questionType.strip().toLowerCase(Locale.ROOT);
        if ("choice".equals(type)) {
            return user.replace(" ", "").equals(expected.replace(" ", ""));
        }
        if ("short".equals(type)) {
            if (user.equals(expected)) {
                return true;
            }
            if (expected.length() <= SHORT_FUZZY_MAX_LENGTH) {
                return ratio(user, expected) >= SHORT_THRESHOLD;
            }
            return false;
        }
        if ("open".equals(type)) {
            List<String> keywords = new ArrayList<>();
            for (String keyword : expected.split(OPEN_DELIMITER)) {
                String value = keyword.strip();
                if (!value.isEmpty()) {
                    keywords.add(value);
                }
            }
            if (keywords.isEmpty()) {
                return false;
            }
            int matched = 0;
            for (String keyword : keywords) {
                if (user.contains(keyword)) {
                    matched++;
                }
            }
            return (double) matched / keywords.size() >= OPEN_KEYWORD_RATIO;
        }
        return false;
    }

    /**
     * {@code difflib.SequenceMatcher.ratio()}：{@code 2 * M / T}。
     *
     * <p>M 是递归最长匹配块的总长度。字符串很短（≤30 字）时无需 autojunk 启发式。
     */
    static double ratio(String a, String b) {
        int total = a.length() + b.length();
        if (total == 0) {
            return 1.0;
        }
        return 2.0 * matchingChars(a, b, 0, a.length(), 0, b.length()) / total;
    }

    private static int matchingChars(String a, String b, int aLo, int aHi, int bLo, int bHi) {
        Block block = longestMatch(a, b, aLo, aHi, bLo, bHi);
        if (block.size() == 0) {
            return 0;
        }
        return block.size()
                + matchingChars(a, b, aLo, block.aStart(), bLo, block.bStart())
                + matchingChars(a, b, block.aStart() + block.size(), aHi,
                        block.bStart() + block.size(), bHi);
    }

    /** 最长连续匹配块；同长度时取 a 更早、再取 b 更早（与 difflib 一致）。 */
    private static Block longestMatch(String a, String b, int aLo, int aHi, int bLo, int bHi) {
        int bestSize = 0;
        int bestA = aLo;
        int bestB = bLo;
        int width = bHi - bLo;
        int[] previous = new int[width + 1];
        for (int i = 1; i <= aHi - aLo; i++) {
            int[] current = new int[width + 1];
            char aChar = a.charAt(aLo + i - 1);
            for (int j = 1; j <= width; j++) {
                if (aChar != b.charAt(bLo + j - 1)) {
                    continue;
                }
                current[j] = previous[j - 1] + 1;
                int size = current[j];
                int aStart = aLo + i - size;
                int bStart = bLo + j - size;
                if (size > bestSize
                        || (size == bestSize
                        && (aStart < bestA || (aStart == bestA && bStart < bestB)))) {
                    bestSize = size;
                    bestA = aStart;
                    bestB = bStart;
                }
            }
            previous = current;
        }
        return new Block(bestSize, bestA, bestB);
    }

    private static String stripLower(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private record Block(int size, int aStart, int bStart) {
    }
}
