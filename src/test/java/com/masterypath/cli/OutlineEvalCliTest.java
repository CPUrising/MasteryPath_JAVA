package com.masterypath.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 评测 CLI（参考实现 {@code eval/__main__.py} + {@code tests/test_eval.py:104-136}）。
 *
 * <p>退出码是 CI 契约：达标 0，低于阈值 / 有环 / 越界 / 基线退化 1。
 * 测试直接调 {@link OutlineEvalCli#run(String[], PrintStream, PrintStream)}，
 * 不触发 {@code System.exit}。
 */
class OutlineEvalCliTest {

    private static final ObjectMapper MAPPER = JsonSupport.objectMapper();

    @TempDir
    Path tempDir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    @DisplayName("参考用例完全匹配 → 退出码 0，报告进 stdout")
    void passesOnMatchingCase() throws IOException {
        Path cases = writeCases(List.of(testCase("c1",
                modules(kp("理解线程", "concept")), modules(kp("理解线程", "concept")))));

        int code = run("outline", "--cases", cases.toString());

        assertThat(code).isZero();
        Map<String, Object> report = report();
        assertThat(report).containsEntry("mean_kp_f1", 1.0)
                .containsEntry("all_acyclic", true)
                .containsEntry("all_in_bounds", true);
        assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    @DisplayName("前置成环 → 退出码 1，stderr 报 cycle")
    void failsOnCycle() throws IOException {
        List<Map<String, Object>> cyclic = modules(
                kp("alpha", List.of("beta")), kp("beta", List.of("alpha")));
        Path cases = writeCases(List.of(testCase("c1", cyclic, null)));

        int code = run("outline", "--cases", cases.toString());

        assertThat(code).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("prerequisite cycle detected");
    }

    @Test
    @DisplayName("mean_kp_f1 低于 --min-f1 → 退出码 1")
    void failsBelowMinF1() throws IOException {
        Path cases = writeCases(List.of(testCase("c1",
                modules(kp("alpha")), modules(kp("完全不同的名字")))));

        int code = run("outline", "--cases", cases.toString());

        assertThat(code).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("mean_kp_f1").contains("< 0.8");
    }

    @Test
    @DisplayName("KP 数越过 60 → 退出码 1")
    void failsOutOfBounds() throws IOException {
        Path cases = writeCases(List.of(testCase("c1",
                modules(kp("alpha")), modules(repeatKp(61)))));

        int code = run("outline", "--cases", cases.toString());

        assertThat(code).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8))
                .contains("knowledge-point count out of bounds");
    }

    @Test
    @DisplayName("相对基线退化超过 0.05 → 退出码 1")
    void failsOnBaselineRegression() throws IOException {
        Path cases = writeCases(List.of(testCase("c1",
                modules(kp("alpha")), modules(kp("beta")))));
        Path baseline = tempDir.resolve("baseline.json");
        Files.writeString(baseline, "{\"mean_kp_f1\": 1.0}", StandardCharsets.UTF_8);

        int code = run("outline", "--cases", cases.toString(),
                "--baseline", baseline.toString());

        assertThat(code).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("regression vs baseline");
    }

    @Test
    @DisplayName("退化在容差内 → 退出码 0")
    void toleratesSmallRegression() throws IOException {
        // 当前 f1 = 1.0（同名），基线 = 1.02 → 1.02 - 0.05 = 0.97，1.0 不低于它。
        Path cases = writeCases(List.of(testCase("c1",
                modules(kp("alpha")), modules(kp("alpha")))));
        Path baseline = tempDir.resolve("baseline.json");
        Files.writeString(baseline, "{\"mean_kp_f1\": 1.02}", StandardCharsets.UTF_8);

        int code = run("outline", "--cases", cases.toString(),
                "--baseline", baseline.toString());

        assertThat(code).isZero();
    }

    @Test
    @DisplayName("缺 --cases → 退出码 2")
    void missingCasesIsUsageError() {
        assertThat(run("outline")).isEqualTo(2);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("--cases");
    }

    @Test
    @DisplayName("未知子命令 → 退出码 2")
    void unknownCommandIsUsageError() throws IOException {
        Path cases = writeCases(List.of(testCase("c1", modules(kp("a")), modules(kp("a")))));

        assertThat(run("nope", "--cases", cases.toString())).isEqualTo(2);
    }

    // -- 辅助 -----------------------------------------------------------------

    private int run(String... args) {
        return OutlineEvalCli.run(args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private Map<String, Object> report() throws IOException {
        return MAPPER.readValue(out.toString(StandardCharsets.UTF_8), Map.class);
    }

    private Path writeCases(List<Map<String, Object>> cases) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map<String, Object> testCase : cases) {
            body.append(MAPPER.writeValueAsString(testCase)).append('\n');
        }
        Path path = tempDir.resolve("cases.jsonl");
        Files.writeString(path, body.toString(), StandardCharsets.UTF_8);
        return path;
    }

    private static Map<String, Object> testCase(String id, List<Map<String, Object>> reference,
                                                List<Map<String, Object>> generated) {
        Map<String, Object> testCase = new LinkedHashMap<>();
        testCase.put("id", id);
        testCase.put("reference_modules", reference);
        if (generated != null) {
            testCase.put("generated_modules", generated);
        }
        return testCase;
    }

    @SafeVarargs
    private static List<Map<String, Object>> modules(Map<String, Object>... points) {
        Map<String, Object> module = new LinkedHashMap<>();
        module.put("name", "M");
        module.put("knowledge_points", List.of(points));
        List<Map<String, Object>> modules = new ArrayList<>();
        modules.add(module);
        return modules;
    }

    private static Map<String, Object> kp(String name, String type) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("name", name);
        point.put("type", type);
        return point;
    }

    private static Map<String, Object> kp(String name) {
        return kp(name, "memory");
    }

    private static Map<String, Object> kp(String name, List<String> prerequisites) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("name", name);
        point.put("prerequisites", prerequisites);
        return point;
    }

    private static Map<String, Object>[] repeatKp(int count) {
        @SuppressWarnings("unchecked")
        Map<String, Object>[] points = new Map[count];
        for (int index = 0; index < count; index++) {
            points[index] = kp("p" + index);
        }
        return points;
    }
}
