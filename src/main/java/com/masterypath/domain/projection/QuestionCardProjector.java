package com.masterypath.domain.projection;

import com.masterypath.domain.model.PendingOption;
import com.masterypath.domain.model.PendingQuestion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习者可见投影（{@code INV-07}）。
 *
 * <p><b>纯函数层</b>：零 I/O、零 Spring。用<b>白名单</b>构造投影，而不是从完整对象里排除敏感字段：
 * 黑名单在将来新增敏感字段时会静默泄漏，白名单只会「不展示」，是 fail-closed。
 *
 * <p>投影恰好 7 个键（与参考实现 {@code public_pending_question} 一致）：
 * {@code question_id} / {@code knowledge_point_id} / {@code module_id} / {@code prompt} /
 * {@code question_type} / {@code options} / {@code difficulty}。
 *
 * <p>MUST NOT 出现：{@code expected_answer}、{@code explanation}、{@code accepted_answers}、
 * {@code required_keywords}、{@code optional_keywords}、{@code created_at}。
 */
public final class QuestionCardProjector {

    private QuestionCardProjector() {
    }

    /**
     * 把待答题目投影为学习者可见的 map。
     *
     * @param pending 服务端完整题目
     * @return 只含白名单字段的新 map（可安全序列化给前端）
     */
    public static Map<String, Object> publicPendingQuestion(PendingQuestion pending) {
        Map<String, Object> publicView = new LinkedHashMap<>();
        if (pending == null) {
            return publicView;
        }
        publicView.put("question_id", pending.getQuestionId());
        publicView.put("knowledge_point_id", pending.getKnowledgePointId());
        publicView.put("module_id", pending.getModuleId());
        publicView.put("prompt", pending.getPrompt());
        publicView.put("question_type", pending.getQuestionType().value());
        publicView.put("options", publicOptions(pending));
        publicView.put("difficulty", pending.getDifficulty());
        return publicView;
    }

    /** 选项投影：只保留 {@code label} 与 {@code body}。 */
    private static List<Map<String, Object>> publicOptions(PendingQuestion pending) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (PendingOption option : pending.getOptions()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("label", option.getLabel());
            entry.put("body", option.getBody());
            options.add(entry);
        }
        return options;
    }
}
