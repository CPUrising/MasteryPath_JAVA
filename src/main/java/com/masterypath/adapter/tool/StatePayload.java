package com.masterypath.adapter.tool;

import com.masterypath.domain.mode.SessionMode;
import com.masterypath.service.LearningService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导师视角的状态快照（参考实现 {@code _state_payload}）。
 *
 * <p><b>唯一会改写服务层返回值的地方</b>：{@code get_state} 对无目标路径返回
 * {@code next=complete}，但导师视角不是「学完了」，而是「还没设计」。
 * 因此这里把 {@code status} 改成 {@code empty} 并附 {@code message}，
 * 避免模型对不存在的课程说「恭喜学完」。
 */
final class StatePayload {

    private StatePayload() {
    }

    /**
     * 构造导师视角快照。
     *
     * @param service 学习服务
     * @param pathId  当前路径
     * @param mode    当前会话模式原始值
     * @return 快照（{@code status} ∈ {@code empty} / {@code active}）
     */
    static Map<String, Object> of(LearningService service, String pathId, Object mode) {
        Map<String, Object> state = service.getState(pathId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "empty");
        payload.put("path_id", pathId);
        payload.put("name", state.getOrDefault("name", ""));
        payload.put("goal", state.getOrDefault("goal", ""));
        payload.put("mode", SessionMode.normalize(mode).value());
        payload.put("next", Map.of());
        payload.put("map", Map.of());
        payload.put("pending_question", null);
        payload.put("last_answered", Map.of("status", "none"));
        payload.put("due_reviews", List.of());
        payload.put("sources", List.of());
        payload.put("learner_profile", state.get("learner_profile"));
        payload.put("intake_needed", !ToolSupport.bool(state.get("learner_profile")));
        if (!"active".equals(state.get("status"))) {
            payload.put("message",
                    "No mastery path is active yet. Design one with mastery_build.");
            return payload;
        }
        payload.put("next", state.getOrDefault("next", Map.of()));
        payload.put("map", state.getOrDefault("map", Map.of()));
        payload.put("pending_question", state.get("pending_question"));
        payload.put("last_answered", state.getOrDefault("last_answered", Map.of()));
        payload.put("due_reviews", state.getOrDefault("due_reviews", List.of()));
        payload.put("sources", state.getOrDefault("sources", List.of()));
        if (!ToolSupport.bool(payload.get("map"))) {
            payload.put("next", Map.of());
            payload.put("message", "This goal has no outline yet. Design one with mastery_build.");
            return payload;
        }
        payload.put("status", "active");
        payload.put("learning_stages", state.getOrDefault("learning_stages", Map.of()));
        return payload;
    }

    /** 把快照序列化成 JSON 文本（工具 {@code content} 用）。 */
    static String json(Map<String, Object> payload) {
        try {
            return com.masterypath.domain.model.JsonSupport.objectMapper()
                    .writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 把任意对象序列化成 JSON 文本。 */
    static String jsonOf(Object value) {
        try {
            return com.masterypath.domain.model.JsonSupport.objectMapper()
                    .writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 构造保持插入顺序的 map；键值交替传入。 */
    static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }

    /** 空列表（避免各处重复 {@code new ArrayList<>()}）。 */
    static List<Object> emptyList() {
        return new ArrayList<>();
    }
}
