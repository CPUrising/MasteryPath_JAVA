package com.masterypath.service;

import com.masterypath.domain.model.LearningModule;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.domain.policy.MasteryPolicy;
import com.masterypath.storage.LearningStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 只读导航（参考实现 {@code learning/navigation.py} + {@code nav_tools.py} 的 {@code _handoff}）。
 *
 * <p><b>本类从不写库</b>：路径拥有地图与进度，会话拥有消息与模式，两者生命周期不同；
 * 导航只负责「看」和「生成交接卡」，由前端决定学习者是否接受（{@code ADR-022}）。
 *
 * <p>工具层的参数解析与失败文案（{@code path_id is required; ...}）属 {@code 0015}，
 * 本类只提供它们背后的数据构造。
 */
public final class NavigationService {

    /** 课程卡片上限。 */
    public static final int TOPIC_LIMIT = 20;
    /** 会话列表上限。 */
    public static final int SESSION_LIMIT = 20;
    /** 单张课程卡片里的模块上限。 */
    public static final int MODULE_LIMIT = 40;

    private final LearningStore store;

    public NavigationService(LearningStore store) {
        this.store = store;
    }

    // -- 课程卡片 -------------------------------------------------------------

    /**
     * 学习者可被送回的全部已建课程（参考实现 {@code topic_cards}）。
     *
     * <p>筛选：空 map 的路径跳过；{@code query} 非空时按「名称 + 目标 + 模块名」子串匹配
     * （<b>不含</b> {@code path_id}，也不含模块 {@code objective} 与知识点名）。
     *
     * <p>条件键：{@code modules_omitted} / {@code topics_omitted} 只在超限时出现；
     * {@code query} 只在原始 query 为真值时出现（值为原始 query，不是折叠后的 needle）。
     */
    public Map<String, Object> topicCards(String query) {
        String rawQuery = query == null ? "" : query;
        String needle = collapseWhitespace(rawQuery).toLowerCase(Locale.ROOT);
        List<Map<String, Object>> cards = new ArrayList<>();
        for (LearningProgress progress : store.listPaths()) {
            Map<String, Object> counts = counts(progress);
            if (((Number) counts.get("total")).intValue() <= 0) {
                continue;
            }
            List<Map<String, Object>> modules = modules(progress);
            List<String> parts = new ArrayList<>();
            parts.add(progress.getName() == null || progress.getName().isEmpty()
                    ? progress.getPathId() : progress.getName());
            parts.add(progress.getGoal());
            for (Map<String, Object> module : modules) {
                parts.add(String.valueOf(module.get("name")));
            }
            String haystack = String.join(" ", parts).toLowerCase(Locale.ROOT);
            if (!needle.isEmpty() && !haystack.contains(needle)) {
                continue;
            }
            Map<String, Object> card = payload(
                    "path_id", progress.getPathId(),
                    "name", progress.getName() == null || progress.getName().isEmpty()
                            ? progress.getPathId() : progress.getName(),
                    "goal", progress.getGoal(),
                    "objectives", counts.get("total"),
                    "mastered", counts.get("mastered"),
                    "learning", counts.get("learning"),
                    "not_started", counts.get("new"),
                    "due_reviews", MasteryPolicy.dueReviews(progress).size(),
                    "sessions", store.listSessions(progress.getPathId()).size(),
                    "modules", modules.size() > MODULE_LIMIT
                            ? new ArrayList<>(modules.subList(0, MODULE_LIMIT)) : modules);
            if (modules.size() > MODULE_LIMIT) {
                card.put("modules_omitted", modules.size() - MODULE_LIMIT);
            }
            cards.add(card);
        }
        int clipped = Math.max(0, cards.size() - TOPIC_LIMIT);
        List<Map<String, Object>> visible = cards.size() > TOPIC_LIMIT
                ? new ArrayList<>(cards.subList(0, TOPIC_LIMIT)) : cards;
        Map<String, Object> result = payload(
                "topics", visible,
                "total_topics", visible.size() + clipped);
        if (clipped > 0) {
            result.put("topics_omitted", clipped);
        }
        if (!rawQuery.isEmpty()) {
            result.put("query", rawQuery);
        }
        return result;
    }

    /**
     * 按 {@code path_id} 取单条课程（参考实现 {@code find_topic}）。
     *
     * <p>空 {@code pathId} 直接返回 {@code null} 且<b>不查库</b>；空 map 的路径视为不存在。
     * 与 {@link #topicCards} 不同，这里的 {@code modules} <b>不截断</b>。
     */
    public Map<String, Object> findTopic(String pathId) {
        if (pathId == null || pathId.isEmpty()) {
            return null;
        }
        LearningProgress progress = store.load(pathId.strip());
        if (progress == null) {
            return null;
        }
        Map<String, Object> counts = counts(progress);
        if (((Number) counts.get("total")).intValue() <= 0) {
            return null;
        }
        return payload(
                "path_id", progress.getPathId(),
                "name", progress.getName() == null || progress.getName().isEmpty()
                        ? progress.getPathId() : progress.getName(),
                "goal", progress.getGoal(),
                "objectives", counts.get("total"),
                "mastered", counts.get("mastered"),
                "learning", counts.get("learning"),
                "not_started", counts.get("new"),
                "due_reviews", MasteryPolicy.dueReviews(progress).size(),
                "sessions", store.listSessions(progress.getPathId()).size(),
                "modules", modules(progress));
    }

    // -- 模块解析 -------------------------------------------------------------

    /**
     * 把学习者说的「第 1 课」「Statistics basics」「m1」解析成模块（参考实现 {@code resolve_module}）。
     *
     * <p>逐模块依次试三种匹配，命中即返回：{@code module_id} 相等 → {@code name} 相等 →
     * 序号相等（<b>1 基</b>，即 {@code order + 1}）。空 {@code moduleRef} 返回 {@code null}。
     */
    public Map<String, Object> resolveModule(Map<String, Object> topic, String moduleRef) {
        String ref = collapseWhitespace(moduleRef == null ? "" : moduleRef);
        if (ref.isEmpty()) {
            return null;
        }
        String folded = ref.toLowerCase(Locale.ROOT);
        Object rawModules = topic == null ? null : topic.get("modules");
        if (!(rawModules instanceof List<?> list)) {
            return null;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> module)) {
                continue;
            }
            if (String.valueOf(module.get("module_id")).toLowerCase(Locale.ROOT).equals(folded)) {
                return castMap(module);
            }
            if (String.valueOf(module.get("name")).toLowerCase(Locale.ROOT).equals(folded)) {
                return castMap(module);
            }
            try {
                int wanted = Integer.parseInt(stripTrailingDots(ref).trim());
                Object order = module.get("order");
                int moduleOrder = order instanceof Number number ? number.intValue() : -1;
                if (wanted == moduleOrder + 1) {
                    return castMap(module);
                }
            } catch (NumberFormatException ignored) {
                // 不是数字：只按 id / 名称匹配。
            }
        }
        return null;
    }

    // -- 会话列表 -------------------------------------------------------------

    /**
     * 某课程下的会话（参考实现 {@code topic_sessions}）。
     *
     * <p>跳过 {@code preferences.archived} 为真值的会话；{@code awaiting_answer} 需要
     * 「路径有 pending + pending 有 question_id + 该会话 has_pending_question」三者同时成立。
     * {@code total_sessions} 是<b>截断前</b>的总数。
     */
    public Map<String, Object> topicSessions(String pathId) {
        LearningProgress progress = store.load(pathId);
        if (progress == null) {
            return payload("sessions", new ArrayList<>(), "total_sessions", 0);
        }
        PendingQuestion pending = progress.getPendingQuestion();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> session : store.listSessions(pathId)) {
            Object rawPreferences = session.get("preferences");
            if (rawPreferences instanceof Map<?, ?> preferences
                    && truthy(preferences.get("archived"))) {
                continue;
            }
            boolean awaiting = pending != null
                    && pending.getQuestionId() != null
                    && !pending.getQuestionId().isEmpty()
                    && truthy(session.get("has_pending_question"));
            rows.add(payload(
                    "session_id", session.get("session_id"),
                    "title", strOrEmpty(session.get("title")),
                    "messages", toInt(session.get("message_count")),
                    "updated_at", toDouble(session.get("updated_at")),
                    "running", "running".equals(session.get("status")),
                    "awaiting_answer", awaiting,
                    "last_message", truncate(strOrEmpty(session.get("last_message")), 240)));
        }
        rows.sort((left, right) -> Double.compare(
                ((Number) right.get("updated_at")).doubleValue(),
                ((Number) left.get("updated_at")).doubleValue()));
        int total = rows.size();
        int clipped = Math.max(0, total - SESSION_LIMIT);
        List<Map<String, Object>> visible = total > SESSION_LIMIT
                ? new ArrayList<>(rows.subList(0, SESSION_LIMIT)) : rows;
        Map<String, Object> result = payload("sessions", visible, "total_sessions", total);
        if (clipped > 0) {
            result.put("sessions_omitted", clipped);
        }
        return result;
    }

    // -- hand-off 卡片 --------------------------------------------------------

    /**
     * 生成一张交接卡（参考实现 {@code nav_tools._handoff}）。
     *
     * <p>17 键；{@code session == null} 时 {@code session_*} 五键取默认值，
     * {@code module == null} 时 {@code module_*} 两键为 {@code ""}。
     * {@code emoji} 恒为 {@code ""}（前端自行决定图标）。
     *
     * <p>纯函数：不写库、不发事件。
     */
    public Map<String, Object> handoff(String kind, Map<String, Object> topic,
                                       Map<String, Object> module, String openingMessage,
                                       String reason, Map<String, Object> session) {
        Map<String, Object> safeTopic = topic == null ? Map.of() : topic;
        Map<String, Object> safeModule = module == null ? Map.of() : module;
        Map<String, Object> safeSession = session == null ? Map.of() : session;
        return payload(
                "kind", kind,
                "path_id", strOrEmpty(safeTopic.get("path_id")),
                "path_name", strOrEmpty(safeTopic.get("name")),
                "emoji", "",
                "session_id", strOrEmpty(safeSession.get("session_id")),
                "session_title", strOrEmpty(safeSession.get("title")),
                "session_messages", toInt(safeSession.get("messages")),
                "session_updated_at", toDouble(safeSession.get("updated_at")),
                "session_awaiting", truthy(safeSession.get("awaiting_answer")),
                "session_running", truthy(safeSession.get("running")),
                "module_id", strOrEmpty(safeModule.get("module_id")),
                "module_name", strOrEmpty(safeModule.get("name")),
                "opening_message", openingMessage == null ? "" : openingMessage,
                "reason", reason == null ? "" : reason,
                "due_reviews", toInt(safeTopic.get("due_reviews")),
                "mastered", toInt(safeTopic.get("mastered")),
                "objectives", toInt(safeTopic.get("objectives")));
    }

    // -- 辅助 -----------------------------------------------------------------

    /** 状态计数：{@code mastered} / {@code learning} / {@code new} / {@code total}。 */
    private static Map<String, Object> counts(LearningProgress progress) {
        Map<String, Map<String, Object>> summary = MasteryPolicy.mapSummary(progress);
        Map<String, Object> counts = payload(
                "mastered", 0, "learning", 0, "new", 0, "total", summary.size());
        for (Map<String, Object> entry : summary.values()) {
            String status = String.valueOf(entry.get("status"));
            counts.put(status, ((Number) counts.get(status)).intValue() + 1);
        }
        return counts;
    }

    /** 模块大纲，按 {@code order} 升序（参考实现 {@code _modules}）。 */
    private static List<Map<String, Object>> modules(LearningProgress progress) {
        List<LearningModule> ordered = new ArrayList<>(progress.getModules());
        ordered.sort((left, right) -> Integer.compare(left.getOrder(), right.getOrder()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LearningModule module : ordered) {
            rows.add(payload(
                    "module_id", module.getId(),
                    "name", module.getName(),
                    "order", module.getOrder(),
                    "objective", module.getObjective(),
                    "objectives", module.getKnowledgePoints().size()));
        }
        return rows;
    }

    /** Python 的 {@code " ".join(s.split())}：折叠全部空白并去首尾。 */
    private static String collapseWhitespace(String text) {
        return String.join(" ", text.trim().split("\\s+"));
    }

    /** Python 的 {@code s.rstrip(".")}：只去尾部句点。 */
    private static String stripTrailingDots(String text) {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '.') {
            end--;
        }
        return text.substring(0, end);
    }

    /**
     * Python 真值语义：{@code None} / {@code False} / {@code 0} / {@code ""} / 空容器为假。
     *
     * <p>不能只判 {@code Boolean.TRUE}：库里可能存字符串 {@code "true"} 或数字 {@code 1}。
     */
    private static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0;
        }
        if (value instanceof CharSequence text) {
            return text.length() > 0;
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private static String strOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    /** Python 的 {@code s[:limit]}：按字符数截断。 */
    private static String truncate(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    /** 构造保持插入顺序的 payload；键值交替传入。 */
    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            map.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return map;
    }
}
