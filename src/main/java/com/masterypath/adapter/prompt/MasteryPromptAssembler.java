package com.masterypath.adapter.prompt;

import com.masterypath.domain.mode.BehaviorBudget;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * mastery 系统提示词装配（参考实现 {@code MasteryPromptAssembler}）。
 *
 * <p>块顺序是协议的一部分：身份 → 本次会话的职责 → 运行时事实 → 运行时策略 →
 * 回合协议 → 教学 playbook → 工具调用上限 → 工具清单。渲染格式复刻
 * {@code LoopPromptAssembler.render}：`## 块名` + 正文，块之间 `---`，空块省略。
 */
public final class MasteryPromptAssembler {

    private final MasteryPrompts prompts;

    public MasteryPromptAssembler(MasteryPrompts prompts) {
        this.prompts = prompts == null ? MasteryPrompts.shared() : prompts;
    }

    /**
     * 装配一次回合的系统提示词。
     *
     * @param mode      会话模式（未知值按 {@code study} 归一）
     * @param toolNames 本回合挂载的工具名（顺序即清单顺序）
     * @param language  响应语言（{@code general.response_language}）
     * @param today     运行时日期
     */
    public String systemPrompt(Object mode, Collection<String> toolNames,
                               String language, LocalDate today) {
        List<Block> blocks = new ArrayList<>();
        add(blocks, "mastery_tutor", prompts.general());
        add(blocks, "mastery_session_mode", prompts.session(mode));
        add(blocks, "runtime_context", prompts.runtimeContext(today));
        add(blocks, "runtime_policy", prompts.runtimePolicy());
        add(blocks, "mastery_loop", prompts.loopSystem());
        add(blocks, "mastery_playbook", prompts.playbook());
        add(blocks, "tool_call_policy",
                prompts.toolCallPolicy(BehaviorBudget.MAX_TOOL_CALLS_PER_TURN));
        add(blocks, "tools", prompts.toolManifest(toolNames));
        String prompt = render(blocks);
        String directive = prompts.languageDirective(language, true);
        if (prompt.isEmpty()) {
            return directive;
        }
        return directive.isEmpty() ? prompt : prompt + "\n\n" + directive;
    }

    /** 渲染块列表（空块跳过）。 */
    static String render(List<Block> blocks) {
        List<String> rendered = new ArrayList<>();
        for (Block block : blocks) {
            if (block.content() == null || block.content().isBlank()) {
                continue;
            }
            rendered.add("## " + block.name() + "\n" + block.content().strip());
        }
        return String.join("\n\n---\n\n", rendered);
    }

    private static void add(List<Block> blocks, String name, String content) {
        if (content != null && !content.isBlank()) {
            blocks.add(new Block(name, content));
        }
    }

    /** 一个提示词块。 */
    record Block(String name, String content) {
    }
}
