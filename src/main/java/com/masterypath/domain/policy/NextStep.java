package com.masterypath.domain.policy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 「下一个目标」的结论（由门槛算出，不是阶段游标）。
 *
 * <p>与参考实现的 {@code NextStep} 一一对应：字段名保持 snake_case 以便直接序列化给前端。
 *
 * @param action             {@code answer_pending} | {@code probe} | {@code teach} |
 *                           {@code practice} | {@code assess} | {@code review} | {@code complete}
 * @param moduleId           所属模块 id
 * @param moduleName         所属模块名
 * @param knowledgePointId   目标知识点 id
 * @param knowledgePointName 目标知识点名
 * @param knowledgePointType 目标知识点类型
 * @param status             该目标的派生状态（{@code new}/{@code learning}/{@code mastered}）
 * @param gate               该目标的门槛种类（{@code qualitative}/{@code quantitative}）
 * @param mastery            当前掌握度（定性通过时显示 1.0）
 * @param threshold          该类型的门槛（仅展示）
 * @param reason             结论依据（便于调试与留痕）
 * @param forgettingRisk     到期复习的遗忘风险（非复习步骤为 0）
 * @param pendingPrompt      待答题的题面（仅 {@code answer_pending} 时有值）
 */
public record NextStep(
        String action,
        String moduleId,
        String moduleName,
        String knowledgePointId,
        String knowledgePointName,
        String knowledgePointType,
        String status,
        String gate,
        double mastery,
        double threshold,
        String reason,
        double forgettingRisk,
        String pendingPrompt) {

    /** 全部目标已掌握或已延后。 */
    public static NextStep complete(String reason) {
        return new NextStep("complete", "", "", "", "", "", "", "", 0.0, 0.0, reason, 0.0, "");
    }

    /** 存在未作答的题目：必须先作答，不能推进。 */
    public static NextStep answerPending(String knowledgePointId, String prompt) {
        return new NextStep("answer_pending", "", "", knowledgePointId, "", "", "", "",
                0.0, 0.0, "open question needs an answer", 0.0,
                prompt == null ? "" : prompt);
    }

    /** 序列化为 REST 友好的 map（字段名与参考实现一致）。 */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("action", action);
        map.put("module_id", moduleId);
        map.put("module_name", moduleName);
        map.put("knowledge_point_id", knowledgePointId);
        map.put("knowledge_point_name", knowledgePointName);
        map.put("knowledge_point_type", knowledgePointType);
        map.put("status", status);
        map.put("gate", gate);
        map.put("mastery", mastery);
        map.put("threshold", threshold);
        map.put("reason", reason);
        map.put("forgetting_risk", forgettingRisk);
        map.put("pending_prompt", pendingPrompt);
        return map;
    }
}
