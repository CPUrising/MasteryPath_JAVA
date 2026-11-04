package com.masterypath.domain.mode;

/**
 * 导师行为预算（ADR-019 / {@code INV-60}）。
 *
 * <p>三个上限集中在一处，使回合循环、工具层与测试读同一个来源。
 * 本类只声明常量，不含判定逻辑——消费点分别在 {@code 0013}（单回合调用数）、
 * {@code 0008}（连续追问与同题提示）。
 */
public final class BehaviorBudget {

    /** 单回合工具调用上限；超出后回合以 {@code reason=budget} 结束。 */
    public static final int MAX_TOOL_CALLS_PER_TURN = 6;

    /** 连续追问上限；达到后必须先由学习者作答。 */
    public static final int MAX_CONSECUTIVE_QUESTIONS = 3;

    /** 同一题允许的提示次数上限；超出后正确也降级为 {@code partial}。 */
    public static final int MAX_HINTS_PER_QUESTION = 2;

    private BehaviorBudget() {
    }
}
