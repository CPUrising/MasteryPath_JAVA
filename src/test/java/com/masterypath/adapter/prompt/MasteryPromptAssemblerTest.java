package com.masterypath.adapter.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 系统提示词装配（参考实现 {@code LoopPromptAssembler.render} 与
 * {@code MasteryPromptAssembler.foundation_blocks}）。
 */
class MasteryPromptAssemblerTest {

    private final MasteryPromptAssembler assembler =
            new MasteryPromptAssembler(MasteryPrompts.shared());

    @Test
    @DisplayName("块按导师协议顺序出现，且以 --- 分隔")
    void blocksFollowTheTutorOrder() {
        String prompt = assembler.systemPrompt("study", List.of("rag"), "zh",
                LocalDate.of(2026, 11, 10));

        List<String> names = List.of("mastery_tutor", "mastery_session_mode", "runtime_context",
                "runtime_policy", "mastery_loop", "mastery_playbook", "tool_call_policy", "tools");
        int previous = -1;
        for (String name : names) {
            int index = prompt.indexOf("## " + name + "\n");
            assertThat(index).as(name).isGreaterThan(previous);
            previous = index;
        }
        assertThat(prompt).contains("\n\n---\n\n## mastery_session_mode");
        assertThat(prompt).contains("当前时间：2026年11月10日");
        assertThat(prompt).contains("## tools\n- `rag` —");
        assertThat(prompt).contains("[语言要求 / Language]");
    }

    @Test
    @DisplayName("没有 hint 的工具渲染成 - 无")
    void toolsFallBackToNone() {
        String prompt = assembler.systemPrompt("outline", List.of("mastery_quiz"), "zh",
                LocalDate.of(2026, 11, 10));

        assertThat(prompt).contains("## tools\n- 无");
    }

    @Test
    @DisplayName("空模式按 study 注入")
    void emptyModeFallsBackToStudy() {
        String prompt = assembler.systemPrompt(null, List.of(), "zh",
                LocalDate.of(2026, 11, 10));

        assertThat(prompt).contains(MasteryPrompts.shared().session("study"));
    }

    @Test
    @DisplayName("空提示词包只留下工具清单与语言指令，不产生空块")
    void emptyPackRendersOnlyToolListAndDirective() {
        MasteryPromptAssembler empty =
                new MasteryPromptAssembler(new MasteryPrompts(Map.of(), Map.of()));

        String prompt = empty.systemPrompt("study", List.of("mastery_quiz"), "zh",
                LocalDate.of(2026, 11, 10));

        assertThat(prompt).contains("## tools\n- 无");
        assertThat(prompt)
                .doesNotContain("## mastery_tutor")
                .doesNotContain("## mastery_session_mode")
                .doesNotContain("## mastery_playbook");
        assertThat(prompt).contains("[语言要求 / Language]");
    }
}
