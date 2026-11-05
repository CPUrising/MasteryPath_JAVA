package com.masterypath.adapter.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task Model 建议（参考实现 {@code tests/test_task_model.py}）。
 *
 * <p>核心约束：<b>永不抛异常</b>。任何模型侧失败都必须退到确定性草稿。
 */
class TaskModelTest {

    @Test
    @DisplayName("无客户端：回退文案包含目标知识点名")
    void fallbackWithoutClient() {
        String text = new TaskModel().suggestQuestion(state("practice", "滑窗计数"));
        assertThat(text).isNotEmpty().contains("滑窗计数");
    }

    @Test
    @DisplayName("回退文案随动作变化")
    void fallbackReflectsAction() {
        TaskModel model = new TaskModel();
        assertThat(model.suggestQuestion(state("answer_pending", "滑窗计数"))).contains("卡片");
        assertThat(model.suggestQuestion(state("review", "滑窗计数"))).contains("复习");
    }

    @Test
    @DisplayName("四条动作 + 缺省文案逐字复刻")
    void fallbackTextIsVerbatim() {
        TaskModel model = new TaskModel();
        assertThat(model.suggestQuestion(state("answer_pending", "X")))
                .isEqualTo("先作答上面那张卡片，再继续。");
        assertThat(model.suggestQuestion(state("review", "X")))
                .isEqualTo("复习一下：X 你还记得吗？请用自己的话说说。");
        assertThat(model.suggestQuestion(state("complete", "X")))
                .isEqualTo("这条路径的目标已全部掌握，要不要开始一个新主题？");
        assertThat(model.suggestQuestion(state("assess", "X")))
                .isEqualTo("请用你自己的话解释 X，并举一个例子。");
        assertThat(model.suggestQuestion(state("practice", "X")))
                .isEqualTo("关于 X，你最先想到的是什么？");
    }

    @Test
    @DisplayName("缺知识点名时用「这个知识点」")
    void fallbackUsesDefaultName() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("next", Map.of("action", "practice"));
        assertThat(new TaskModel().suggestQuestion(state))
                .isEqualTo("关于 这个知识点，你最先想到的是什么？");
    }

    @Test
    @DisplayName("有可用模型时原样返回模型输出")
    void usesLlmWhenAvailable() {
        TaskModel model = new TaskModel(() -> new FakeLlmClient(
                List.of(LlmResponse.text("请解释一下滑窗计数"))));
        assertThat(model.suggestQuestion(state("practice", "滑窗计数")))
                .isEqualTo("请解释一下滑窗计数");
    }

    @Test
    @DisplayName("模型抛错时回退，接口不失败")
    void fallsBackWhenLlmRaises() {
        TaskModel model = new TaskModel(() -> {
            throw new LlmError("provider down", "upstream_error");
        });
        assertThat(model.suggestQuestion(state("practice", "滑窗计数"))).isNotEmpty();
    }

    @Test
    @DisplayName("模型返回空白文本时回退")
    void fallsBackWhenTextBlank() {
        TaskModel model = new TaskModel(() -> new FakeLlmClient(
                List.of(LlmResponse.text("   "))));
        assertThat(model.suggestQuestion(state("practice", "滑窗计数")))
                .isEqualTo("关于 滑窗计数，你最先想到的是什么？");
    }

    @Test
    @DisplayName("提示词含动作/目标/类型，且只带最近 6 条对话")
    void promptCarriesRecentTail() {
        FakeLlmClient client = new FakeLlmClient(List.of(LlmResponse.text("q")));
        TaskModel model = new TaskModel(() -> client);

        model.suggestQuestion(state("practice", "滑窗计数"),
                List.of("m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8"));

        List<Map<String, Object>> messages = client.calls().get(0);
        assertThat(messages.get(0).get("role")).isEqualTo("system");
        assertThat(String.valueOf(messages.get(0).get("content")))
                .isEqualTo(TaskModel.SYSTEM_PROMPT);
        String user = String.valueOf(messages.get(1).get("content"));
        assertThat(user).contains("Current action: practice")
                .contains("Objective: 滑窗计数")
                .contains("Objective type: procedure")
                .contains("- m3")
                .doesNotContain("- m2");
        assertThat(client.toolsSeen().get(0)).isEmpty();
    }

    @Test
    @DisplayName("会话名取前 40 字；空目标用「新学习会话」")
    void sessionName() {
        TaskModel model = new TaskModel();
        assertThat(model.suggestSessionName("  learn sliding window  ")).isEqualTo(
                "learn sliding window");
        assertThat(model.suggestSessionName("x".repeat(60))).hasSize(40);
        assertThat(model.suggestSessionName("   ")).isEqualTo("新学习会话");
        assertThat(model.suggestSessionName(null)).isEqualTo("新学习会话");
    }

    private static Map<String, Object> state(String action, String name) {
        Map<String, Object> next = new LinkedHashMap<>();
        next.put("action", action);
        next.put("knowledge_point_name", name);
        next.put("knowledge_point_type", "procedure");
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("next", next);
        return state;
    }
}
