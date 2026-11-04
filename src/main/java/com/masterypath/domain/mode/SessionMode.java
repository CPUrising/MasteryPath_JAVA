package com.masterypath.domain.mode;

/**
 * 会话模式（spec 02 §一）。
 *
 * <p><b>声明顺序即 {@code MODES} 顺序</b>（{@code outline} → {@code study} → {@code review}）。
 * {@link ModeGate#owningModes(String)} 依赖该顺序，{@link ModeGate#wrongModeMessage} 又取
 * 其首项作为建议切换目标，因此顺序不可调整。
 *
 * <p>每个模式携带一段提示词块，供系统提示词与 {@code mastery_mode} 的返回内容使用
 * （参考实现 {@code MODE_INSTRUCTIONS}，逐字一致）。
 */
public enum SessionMode {

    OUTLINE("outline",
            "This mode is for designing the learning outline with the learner. "
                    + "Do not teach or examine: mastery_quiz, mastery_grade and mastery_assess "
                    + "are refused here. Build the first outline with mastery_build, or reshape "
                    + "an existing one with mastery_revise. When the learner is ready to begin, "
                    + "switch to study with mastery_mode."),

    STUDY("study",
            "This mode is for teaching and working the agreed outline forward, one "
                    + "objective at a time, through its hard mastery gate. Do not change the "
                    + "outline here: mastery_build and mastery_revise belong to outline mode. "
                    + "When the learner asks to review, switch to review with mastery_mode."),

    REVIEW("review",
            "This mode is for re-testing objectives the learner has already mastered. "
                    + "Quiz or assess only mastered objectives; the engine refuses new ones. "
                    + "Do not teach new content here: switch to study with mastery_mode.");

    /** 未记录模式时的展示默认值（{@code DEFAULT_MODE}）。 */
    public static final SessionMode DEFAULT_MODE = STUDY;

    private final String value;
    private final String instruction;

    SessionMode(String value, String instruction) {
        this.value = value;
        this.instruction = instruction;
    }

    public String value() {
        return value;
    }

    /** 该模式的提示词块（系统提示词与 {@code mastery_mode} 共用）。 */
    public String instruction() {
        return instruction;
    }

    /**
     * 门控用的归一：缺失或未识别一律返回 {@code null}，表示<b>不门控</b>（{@code INV-09}）。
     *
     * <p>与 {@link #normalize(Object)} 语义不同，不可合并：{@code TC-D4} 要求未知模式放行全部工具，
     * 而准入判定又必须把未知模式当作 {@code study}。
     *
     * @param raw 原始值，可为 {@code null}
     * @return 命中的模式，或 {@code null}
     */
    public static SessionMode enforced(Object raw) {
        return fromValue(raw);
    }

    /**
     * 展示与准入用的归一：缺失或未识别一律回退到 {@link #DEFAULT_MODE}。
     *
     * @param raw 原始值，可为 {@code null}
     * @return 永不为 {@code null}
     */
    public static SessionMode normalize(Object raw) {
        SessionMode mode = fromValue(raw);
        return mode == null ? DEFAULT_MODE : mode;
    }

    /** 宽松解析：忽略大小写与前后空白；未识别返回 {@code null}。 */
    private static SessionMode fromValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String candidate = String.valueOf(raw).trim().toLowerCase();
        if (candidate.isEmpty()) {
            return null;
        }
        for (SessionMode mode : values()) {
            if (mode.value.equals(candidate)) {
                return mode;
            }
        }
        return null;
    }
}
