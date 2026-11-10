package com.masterypath.adapter.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工具 schema 完整性（参考实现 {@code test_every_tool_has_a_schema} 与
 * {@code test_required_parameters_match_the_executors}）。
 */
class ToolSchemasTest {

    @Test
    @DisplayName("每个挂载的工具都有 schema，且 type=object + properties")
    void everyToolHasSchema() {
        Map<String, Tool> tools = MasteryTools.all();

        assertThat(tools).isNotEmpty();
        for (Map.Entry<String, Tool> entry : tools.entrySet()) {
            String name = entry.getKey();
            assertThat(entry.getValue().description()).as(name + " description").isNotEmpty();
            Map<String, Object> schema = ToolSchemas.of(name);
            assertThat(schema).as(name + " schema").containsEntry("type", "object");
            assertThat(schema).as(name + " properties").containsKey("properties");
            assertThat(ToolSchemas.all()).as(name + " registered").containsKey(name);
        }
    }

    @Test
    @DisplayName("schema 表与工具表一一对应，无多余条目")
    void schemaTableMatchesToolTable() {
        assertThat(ToolSchemas.all().keySet())
                .containsExactlyInAnyOrderElementsOf(MasteryTools.all().keySet());
    }

    @Test
    @DisplayName("required 与执行体一致")
    void requiredParametersMatchExecutors() {
        assertThat(required("mastery_quiz"))
                .containsExactly("knowledge_point_id", "question", "expected_answer");
        assertThat(required("mastery_grade")).containsExactly("answer");
        assertThat(required("mastery_skip_question")).isEmpty();
        assertThat(required("mastery_assess")).containsExactly("knowledge_point_id", "passed");
        assertThat(required("mastery_repair_question")).containsExactly("action", "reason");
        assertThat(required("mastery_switch")).containsExactly("path_id");
        assertThat(required("mastery_revise")).containsExactly("ops");
        assertThat(required("mastery_note_gap")).containsExactly("kind");
        assertThat(required("mastery_propose_objective")).containsExactly("name");
        assertThat(required("rag")).containsExactly("query");
        assertThat(required("mastery_status")).isEmpty();
        assertThat(required("mastery_profile")).isEmpty();
    }

    @Test
    @DisplayName("枚举值与领域常量一致")
    void enumsMatchDomainConstants() {
        assertThat(enumOf("mastery_mode", "mode")).containsExactly("outline", "study", "review");
        assertThat(enumOf("mastery_quiz", "question_type"))
                .containsExactly("choice", "short", "open");
        assertThat(enumOf("mastery_quiz", "difficulty"))
                .containsExactly("easy", "medium", "hard");
        assertThat(enumOf("mastery_repair_question", "action")).containsExactly("void", "correct");
        assertThat(enumOf("mastery_profile", "teaching_strategy"))
                .containsExactly("probe_first", "teach_first");
        assertThat(enumOf("mastery_note_gap", "kind"))
                .containsExactly("goal_gap", "source_omission");
    }

    @Test
    @DisplayName("未登记的工具返回空对象 schema")
    void unknownToolFallsBackToEmptySchema() {
        Map<String, Object> schema = ToolSchemas.of("nope");

        assertThat(schema).containsEntry("type", "object");
        assertThat((Map<?, ?>) schema.get("properties")).isEmpty();
        assertThat((List<?>) schema.get("required")).isEmpty();
    }

    // -- 辅助 -----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<String> required(String toolName) {
        return (List<String>) ToolSchemas.of(toolName).get("required");
    }

    @SuppressWarnings("unchecked")
    private static List<String> enumOf(String toolName, String property) {
        Map<String, Object> properties =
                (Map<String, Object>) ToolSchemas.of(toolName).get("properties");
        Map<String, Object> schema = (Map<String, Object>) properties.get(property);
        return (List<String>) schema.get("enum");
    }
}
