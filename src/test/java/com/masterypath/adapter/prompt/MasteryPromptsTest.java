package com.masterypath.adapter.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 提示词包取值（参考实现 {@code test_mastery_mode.py} 的包内容断言）。
 */
class MasteryPromptsTest {

    private final MasteryPrompts prompts = MasteryPrompts.shared();

    @Test
    @DisplayName("身份与运行时策略来自 mastery 包")
    void generalAndRuntimePolicy() {
        assertThat(prompts.general()).contains("一对一掌握式导师");
        assertThat(prompts.runtimePolicy()).contains("<think>");
    }

    @Test
    @DisplayName("三种模式的职责段落互不相同，未知模式按 study")
    void sessionParagraphs() {
        String outline = prompts.session("outline");
        String study = prompts.session("study");
        String review = prompts.session("review");

        assertThat(outline).contains("定下这条精通之路的大纲");
        assertThat(study).contains("这个模式是用来学的");
        assertThat(review).contains("重测已经学过的东西");
        assertThat(List.of(outline, study, review)).doesNotHaveDuplicates();
        assertThat(prompts.session("bogus")).isEqualTo(study);
        assertThat(prompts.session(null)).isEqualTo(study);
    }

    @Test
    @DisplayName("回合协议保留冒号规则与出题即收尾")
    void loopProtocol() {
        assertThat(prompts.loopSystem())
                .contains("冒号")
                .contains("mastery_quiz")
                .contains("出题");
        assertThat(prompts.loopUser()).contains("{user_message}");
        assertThat(prompts.finishExhausted()).isNotBlank();
        assertThat(prompts.settleExhausted()).isNotBlank();
    }

    @Test
    @DisplayName("playbook 保留模式请求与出题区分度")
    void playbook() {
        assertThat(prompts.playbook())
                .contains("那就是一次模式请求")
                .contains("区分度")
                .contains("mastery_mode");
    }

    @Test
    @DisplayName("运行时日期按中文格式填充")
    void runtimeContext() {
        assertThat(prompts.runtimeContext(LocalDate.of(2026, 11, 10)))
                .contains("当前时间：2026年11月10日")
                .doesNotContain("{datetime}");
    }

    @Test
    @DisplayName("工具调用上限填进 chat 兜底文案")
    void toolCallPolicy() {
        assertThat(prompts.toolCallPolicy(6))
                .contains("6")
                .doesNotContain("{limit}");
    }

    @Test
    @DisplayName("工具清单只列有 hint 的工具，空清单写 - 无")
    void toolManifest() {
        assertThat(prompts.toolManifest(List.of("rag")))
                .contains("在用户挂载的某个知识库中检索相关内容")
                .contains("参数格式");
        assertThat(prompts.toolManifest(List.of("mastery_quiz", "mastery_status")))
                .isEqualTo("- 无");
        assertThat(prompts.toolManifest(List.of())).isEqualTo("- 无");
    }

    @Test
    @DisplayName("语言指令区分默认语言并允许用户覆盖")
    void languageDirective() {
        assertThat(prompts.languageDirective("zh", true))
                .contains("[语言要求 / Language]")
                .contains("若用户在对话中明确要求改用其他语言作答");
        assertThat(prompts.languageDirective("en", false))
                .contains("[Language]")
                .doesNotContain("not a restriction on the reader");
    }
}
