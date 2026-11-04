package com.masterypath.domain.grading;

/**
 * 判分结论（对应参考实现的 {@code Decision} dataclass）。
 *
 * @param result   {@code correct} / {@code incorrect} / {@code partial} / {@code ungraded}
 * @param reason   人类可读的原因（{@code ungraded} 时尤其重要，要能告诉模型为什么没判）
 * @param resolved 选择题解析出的选项标签；其他题型为空串
 */
public record Decision(String result, String reason, String resolved) {

    public static final String CORRECT = "correct";
    public static final String INCORRECT = "incorrect";
    public static final String PARTIAL = "partial";
    public static final String UNGRADED = "ungraded";

    public static Decision correct(String reason) {
        return new Decision(CORRECT, reason, "");
    }

    public static Decision incorrect(String reason) {
        return new Decision(INCORRECT, reason, "");
    }

    public static Decision partial(String reason) {
        return new Decision(PARTIAL, reason, "");
    }

    public static Decision ungraded(String reason) {
        return new Decision(UNGRADED, reason, "");
    }

    public boolean isUngraded() {
        return UNGRADED.equals(result);
    }

    public boolean isCorrect() {
        return CORRECT.equals(result);
    }
}
