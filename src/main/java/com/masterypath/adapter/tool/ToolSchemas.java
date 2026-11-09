package com.masterypath.adapter.tool;

import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.quiz.QuizSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 19 个工具的 JSON Schema（参考实现 {@code TOOL_PARAMETERS}）。
 *
 * <p><b>手写而非反射</b>：执行体用 {@code params.get(...)} 取值，方法签名不是可靠来源；
 * 显式 schema 可直接断言，也与参考实现逐字一致。
 *
 * <p>刻意不写 {@code strict}：并非所有 OpenAI 兼容供应商都支持该字段。
 */
public final class ToolSchemas {

    private static final Map<String, Map<String, Object>> SCHEMAS = build();

    private ToolSchemas() {
    }

    /**
     * 取某个工具的 schema。
     *
     * @param toolName 工具名
     * @return schema；未登记的工具返回空对象 schema（与参考实现兜底一致）
     */
    public static Map<String, Object> of(String toolName) {
        Map<String, Object> schema = SCHEMAS.get(toolName);
        return schema == null ? emptySchema() : schema;
    }

    /** 全部 schema（只读视图，供测试断言完整性）。 */
    public static Map<String, Map<String, Object>> all() {
        return SCHEMAS;
    }

    // -- 构造 -----------------------------------------------------------------

    private static Map<String, Map<String, Object>> build() {
        Map<String, Map<String, Object>> schemas = new LinkedHashMap<>();

        schemas.put("mastery_status", schema(Map.of(), List.of()));

        schemas.put("mastery_mode", schema(
                props("mode", enumOf("string", List.of("outline", "study", "review")),
                        "reason", string()),
                List.of("mode")));

        schemas.put("mastery_build", schema(
                props("modules", arrayOf(Map.of("type", "object"),
                                "Outline modules with knowledge_points."),
                        "confirm", bool("Required to rebuild a non-empty outline.")),
                List.of()));

        schemas.put("mastery_revise", schema(
                props("ops", arrayOf(Map.of("type", "object"), "Differential outline operations.")),
                List.of("ops")));

        schemas.put("mastery_quiz", schema(
                props("knowledge_point_id", string(),
                        "question", string(),
                        "expected_answer", string(),
                        "question_type", enumOf("string", QuizSpec.QUESTION_TYPES),
                        "options", arrayOf(optionSchema(), null),
                        "explanation", string(),
                        "difficulty", enumOf("string", QuizSpec.DIFFICULTIES),
                        "accepted_answers", arrayOf(Map.of("type", "string"),
                                "Required for natural-language short answers; equivalent answers "
                                        + "that count as correct."),
                        "required_keywords", arrayOf(Map.of("type", "string"), null),
                        "optional_keywords", arrayOf(Map.of("type", "string"), null)),
                List.of("knowledge_point_id", "question", "expected_answer")));

        schemas.put("mastery_grade", schema(
                props("question_id", string(),
                        "answer", string(),
                        "hints_used", Map.of("type", "integer", "minimum", 0)),
                List.of("question_id", "answer")));

        schemas.put("mastery_skip_question", schema(
                props("question_id", string()), List.of("question_id")));

        schemas.put("mastery_repair_question", schema(
                props("action", enumOf("string", List.of("void", "correct")),
                        "reason", string(),
                        "question_id", string(),
                        "expected_answer", string()),
                List.of("action", "reason")));

        schemas.put("mastery_profile", schema(
                props("prior_knowledge", string(),
                        "target_level", string(),
                        "time_budget", string(),
                        "preferences", string(),
                        "teaching_strategy", enumOf("string", List.of("probe_first", "teach_first")),
                        "notes", string()),
                List.of()));

        schemas.put("mastery_paths", schema(Map.of(), List.of()));

        schemas.put("mastery_switch", schema(
                props("path_id", string()), List.of("path_id")));

        schemas.put("mastery_leave", schema(Map.of(), List.of()));

        schemas.put("mastery_topics", schema(
                props("query", string()), List.of()));

        schemas.put("mastery_sessions", schema(
                props("path_id", string()), List.of("path_id")));

        schemas.put("mastery_open_session", schema(
                props("path_id", string(),
                        "session_id", string(),
                        "opening_message", string(),
                        "module", string(),
                        "reason", string()),
                List.of("path_id", "session_id")));

        schemas.put("mastery_new_session", schema(
                props("path_id", string(),
                        "opening_message", string(),
                        "module", string(),
                        "reason", string()),
                List.of("path_id")));

        schemas.put("rag", schema(
                props("query", string(),
                        "kb_id", string(),
                        "kb_name", string()),
                List.of("query")));

        schemas.put("read_source", schema(
                props("source_id", string()), List.of()));

        schemas.put("mastery_note_explained", schema(
                props("knowledge_point_id", string(),
                        "summary", string()),
                List.of("knowledge_point_id", "summary")));

        schemas.put("mastery_defer_objective", schema(
                props("knowledge_point_id", string(),
                        "note", string()),
                List.of("knowledge_point_id")));

        schemas.put("mastery_assess", schema(
                props("knowledge_point_id", string(),
                        "passed", bool(null),
                        "evidence", string(),
                        "quality", Map.of("type", "number")),
                List.of("knowledge_point_id", "passed")));

        schemas.put("mastery_propose_objective", schema(
                props("name", string(),
                        "type", enumOf("string", knowledgeTypeValues()),
                        "reason", string(),
                        "module_id", string(),
                        "sources", arrayOf(Map.of("type", "string"), null)),
                List.of("name")));

        schemas.put("mastery_note_gap", schema(
                props("kind", enumOf("string", List.of("goal_gap", "source_omission")),
                        "ref", string(),
                        "reason", string()),
                List.of("kind")));

        return Map.copyOf(schemas);
    }

    // -- 构造辅助 -------------------------------------------------------------

    private static Map<String, Object> schema(Map<String, Object> properties,
                                              List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private static Map<String, Object> emptySchema() {
        return schema(Map.of(), List.of());
    }

    /** 按「键, 值, 键, 值…」构造有序 properties。 */
    private static Map<String, Object> props(Object... keyValues) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            properties.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return properties;
    }

    private static Map<String, Object> string() {
        return Map.of("type", "string");
    }

    private static Map<String, Object> bool(String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "boolean");
        if (description != null) {
            schema.put("description", description);
        }
        return schema;
    }

    private static Map<String, Object> enumOf(String type, List<String> values) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        schema.put("enum", values);
        return schema;
    }

    private static Map<String, Object> arrayOf(Map<String, Object> items, String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        if (description != null) {
            schema.put("description", description);
        }
        schema.put("items", items);
        return schema;
    }

    private static Map<String, Object> optionSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props("label", string(), "body", string()));
        schema.put("required", List.of("label", "body"));
        return schema;
    }

    private static List<String> knowledgeTypeValues() {
        List<String> values = new ArrayList<>();
        for (KnowledgeType type : KnowledgeType.values()) {
            values.add(type.value());
        }
        return values;
    }
}
