package com.masterypath.adapter.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务模型的两种「小建议」（参考实现 {@code agents/task_model.py}）。
 *
 * <ol>
 *   <li>{@link #suggestQuestion} —— 给输入框一个草稿问题。<b>永不抛异常</b>：
 *       没有模型、构造失败、调用失败、返回空文本，四条路径都回退到确定性文案。</li>
 *   <li>{@link #suggestSessionName} —— 取目标前 40 字，纯确定性，不调模型。</li>
 * </ol>
 *
 * <p>草稿只是草稿：本类从不发送消息，也不写库。
 */
public class TaskModel {

    /** 系统提示：一句话、学习者语言、只输出问题本身。 */
    static final String SYSTEM_PROMPT =
            "You write ONE short next question for a mastery-learning tutor, in the "
                    + "learner's language. Output only the question, no preamble.";

    /** 回退文案里的缺省知识点名。 */
    static final String DEFAULT_NAME = "这个知识点";

    /** 客户端工厂；可为 {@code null}（表示「没有可用模型」）。 */
    @FunctionalInterface
    public interface ClientFactory {
        LlmClient create();
    }

    private final ClientFactory clientFactory;

    public TaskModel() {
        this(null);
    }

    public TaskModel(ClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    /**
     * 生成一条「下一问」草稿。
     *
     * @param pathState 路径状态快照（读 {@code next} 子字典）
     * @param recent    最近对话；可为 {@code null}
     * @return 非空草稿文本（LLM 成功时是模型输出，否则是确定性回退）
     */
    public String suggestQuestion(Map<String, Object> pathState, List<String> recent) {
        if (clientFactory != null) {
            try {
                LlmClient client = clientFactory.create();
                List<Map<String, Object>> messages = new ArrayList<>();
                messages.add(message("system", SYSTEM_PROMPT));
                messages.add(message("user", prompt(pathState, recent)));
                LlmResponse response = client.chat(messages, List.of());
                String text = response == null || response.text() == null
                        ? "" : response.text().strip();
                if (!text.isEmpty()) {
                    return text;
                }
            } catch (RuntimeException e) {
                // 概率性能力：任何失败都退到确定性草稿，绝不冒泡。
            }
        }
        return fallback(pathState);
    }

    /** 无最近对话的重载。 */
    public String suggestQuestion(Map<String, Object> pathState) {
        return suggestQuestion(pathState, List.of());
    }

    /** 会话名：目标前 40 字；空目标用「新学习会话」。 */
    public String suggestSessionName(String goal) {
        String text = goal == null ? "" : goal.strip();
        return text.isEmpty() ? "新学习会话" : text.substring(0, Math.min(40, text.length()));
    }

    // -- 内部 -----------------------------------------------------------------

    /** 5 条确定性回退，文案逐字复刻参考实现。 */
    static String fallback(Map<String, Object> pathState) {
        Map<String, Object> next = nextOf(pathState);
        Object rawAction = next.get("action");
        String action = rawAction == null ? "" : String.valueOf(rawAction);
        Object rawName = next.get("knowledge_point_name");
        String name = rawName == null || String.valueOf(rawName).isEmpty()
                ? DEFAULT_NAME : String.valueOf(rawName);
        if ("answer_pending".equals(action)) {
            return "先作答上面那张卡片，再继续。";
        }
        if ("review".equals(action)) {
            return "复习一下：" + name + " 你还记得吗？请用自己的话说说。";
        }
        if ("complete".equals(action)) {
            return "这条路径的目标已全部掌握，要不要开始一个新主题？";
        }
        if ("assess".equals(action)) {
            return "请用你自己的话解释 " + name + "，并举一个例子。";
        }
        return "关于 " + name + "，你最先想到的是什么？";
    }

    /** 提示词：动作 + 目标 + 类型，必要时附最近 6 条对话。 */
    static String prompt(Map<String, Object> pathState, List<String> recent) {
        Map<String, Object> next = nextOf(pathState);
        List<String> lines = new ArrayList<>();
        lines.add("Current action: " + str(next.get("action")));
        lines.add("Objective: " + str(next.get("knowledge_point_name")));
        lines.add("Objective type: " + str(next.get("knowledge_point_type")));
        if (recent != null && !recent.isEmpty()) {
            lines.add("Recent conversation:");
            int from = Math.max(0, recent.size() - 6);
            for (String item : recent.subList(from, recent.size())) {
                lines.add("- " + item);
            }
        }
        return String.join("\n", lines);
    }

    private static Map<String, Object> nextOf(Map<String, Object> pathState) {
        if (pathState == null) {
            return Map.of();
        }
        Object next = pathState.get("next");
        if (!(next instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
