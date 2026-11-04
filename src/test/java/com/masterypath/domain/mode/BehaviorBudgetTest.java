package com.masterypath.domain.mode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 行为预算常量（ADR-019 / {@code INV-60}）。
 */
class BehaviorBudgetTest {

    @Test
    @DisplayName("单回合工具调用上限为 6")
    void toolCallsPerTurnIsSix() {
        assertThat(BehaviorBudget.MAX_TOOL_CALLS_PER_TURN).isEqualTo(6);
    }

    @Test
    @DisplayName("连续追问上限为 3")
    void consecutiveQuestionsIsThree() {
        assertThat(BehaviorBudget.MAX_CONSECUTIVE_QUESTIONS).isEqualTo(3);
    }

    @Test
    @DisplayName("同一题提示次数上限为 2")
    void hintsPerQuestionIsTwo() {
        assertThat(BehaviorBudget.MAX_HINTS_PER_QUESTION).isEqualTo(2);
    }

    @Test
    @DisplayName("三个预算均为正数，避免误配成 0 导致一律拒绝")
    void budgetsArePositive() {
        assertThat(BehaviorBudget.MAX_TOOL_CALLS_PER_TURN).isPositive();
        assertThat(BehaviorBudget.MAX_CONSECUTIVE_QUESTIONS).isPositive();
        assertThat(BehaviorBudget.MAX_HINTS_PER_QUESTION).isPositive();
    }
}
