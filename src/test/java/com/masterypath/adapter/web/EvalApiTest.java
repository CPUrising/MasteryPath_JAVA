package com.masterypath.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;
import com.masterypath.domain.model.TopicSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 大纲评测端点（参考实现 {@code tests/test_eval.py:70-101} 的 {@code TC-N5} / {@code TC-N6}）。
 *
 * <p>三条硬性语义：重算而非读存库报告、只读、确定性。
 */
@Tag("api")
class EvalApiTest {

    private static final ObjectMapper MAPPER = JsonSupport.objectMapper();

    private ApiTestSupport api;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport();
    }

    @AfterEach
    void tearDown() {
        api.close();
    }

    @Test
    @DisplayName("来源被引用 → coverage_ratio=1.0 且 passed=true（TC-N5）")
    void evalPassesWhenCovered() throws Exception {
        api.service.createPath("p", "learn X", "X");
        api.service.attachSources("p", List.of(source("a.md")));
        api.service.build("p", outline("a.md"), false);

        Map<String, Object> report = eval("p");

        assertThat(report).containsEntry("coverage_ratio", 1.0)
                .containsEntry("grounding_ratio", 1.0)
                .containsEntry("acyclic", true)
                .containsEntry("passed", true)
                .containsEntry("documents", 1)
                .containsEntry("covered", 1);
        assertThat(toMap(report.get("thresholds")))
                .containsEntry("coverage", 0.9)
                .containsEntry("grounding", 1.0)
                .containsEntry("acyclic", true);
        assertThat((List<?>) report.get("missing")).isEmpty();
    }

    @Test
    @DisplayName("有来源未被引用 → coverage_ratio<1、passed=false、missing 含它")
    void evalReportsUnusedSource() throws Exception {
        api.service.createPath("p", "learn X", "X");
        api.service.attachSources("p", List.of(source("a.md"), source("b.md")));
        api.service.build("p", outline("a.md"), false);

        Map<String, Object> report = eval("p");

        assertThat(report).containsEntry("documents", 2)
                .containsEntry("covered", 1)
                .containsEntry("passed", false);
        assertThat((Double) report.get("coverage_ratio")).isEqualTo(0.5);
        assertThat(labelsOf(report.get("missing"))).containsExactly("b");
    }

    @Test
    @DisplayName("无来源无引用 → documents=0 时 coverage_ratio=1.0，但 grounding=0 使 passed=false")
    void evalWithoutSources() throws Exception {
        api.service.createPath("p", "learn X", "X");
        api.service.build("p", Map.of("modules", List.of(Map.of(
                "name", "M",
                "knowledge_points", List.of(Map.of("name", "fact one", "type", "memory"))))), false);

        Map<String, Object> report = eval("p");

        assertThat(report).containsEntry("documents", 0)
                .containsEntry("coverage_ratio", 1.0)
                .containsEntry("grounding_ratio", 0.0)
                .containsEntry("passed", false);
    }

    @Test
    @DisplayName("含环的前置 → acyclic=false 且 passed=false")
    void evalDetectsCycle() throws Exception {
        api.service.createPath("p", "learn X", "X");
        List<Map<String, Object>> points = new ArrayList<>();
        points.add(Map.of("name", "alpha", "type", "memory", "prerequisites", List.of("beta")));
        points.add(Map.of("name", "beta", "type", "memory", "prerequisites", List.of("alpha")));
        api.service.build("p", Map.of("modules", List.of(Map.of(
                "name", "M", "knowledge_points", points))), false);

        Map<String, Object> report = eval("p");

        assertThat(report).containsEntry("acyclic", false).containsEntry("passed", false);
    }

    @Test
    @DisplayName("确定性：两次调用结果完全一致（TC-N6）")
    void evalIsDeterministic() throws Exception {
        api.service.createPath("p", "learn X", "X");
        api.service.attachSources("p", List.of(source("a.md")));
        api.service.build("p", outline("a.md"), false);

        assertThat(eval("p")).isEqualTo(eval("p"));
    }

    @Test
    @DisplayName("未知路径 → 404 unknown path")
    void evalUnknownPathIs404() throws Exception {
        MvcResult result = api.mvc.perform(post("/learning/paths/nope/eval")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("unknown path");
    }

    // -- 辅助 -----------------------------------------------------------------

    private Map<String, Object> eval(String pathId) throws Exception {
        return MAPPER.readValue(
                api.mvc.perform(post("/learning/paths/" + pathId + "/eval"))
                        .andReturn().getResponse().getContentAsString(),
                Map.class);
    }

    private static Map<String, Object> outline(String fileName) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("name", "obj");
        point.put("type", "concept");
        point.put("sources", List.of(fileName));
        return Map.of("modules", List.of(Map.of(
                "name", "M", "knowledge_points", List.of(point))));
    }

    private static TopicSource source(String fileName) {
        TopicSource source = new TopicSource();
        source.setId(fileName.substring(0, 1));
        source.setKind("file");
        source.setLabel(fileName);
        return source;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static List<String> labelsOf(Object value) {
        List<String> labels = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                labels.add(String.valueOf(((Map<?, ?>) item).get("label")));
            }
        }
        return labels;
    }
}
