package com.masterypath.adapter.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 离线评测指标（参考实现 {@code eval/outline_eval.py} + {@code tests/test_eval.py} 的确定性部分）。
 *
 * <p>核心约束：<b>确定性</b>（同一输入两次结果一致，{@code TC-N6}）与
 * <b>令牌重叠代理</b>（不调模型、不联网）。
 */
class OutlineEvalTest {

    @TempDir
    Path tempDir;

    // -- 令牌化与相似度 -------------------------------------------------------

    @Test
    @DisplayName("令牌化：中文整体成词，英文小写化，保留下划线")
    void tokensAreUnicodeAware() {
        assertThat(OutlineEval.tokens("理解线程")).containsExactly("理解线程");
        assertThat(OutlineEval.tokens("Hello World_1")).containsExactly("hello", "world_1");
        assertThat(OutlineEval.tokens("")).isEmpty();
        assertThat(OutlineEval.tokens(null)).isEmpty();
    }

    @Test
    @DisplayName("相似度是 Jaccard；任一为空返回 0")
    void similarUsesJaccard() {
        assertThat(OutlineEval.similar("理解线程", "理解线程")).isEqualTo(1.0);
        assertThat(OutlineEval.similar("alpha beta", "alpha gamma")).isEqualTo(1.0 / 3.0);
        assertThat(OutlineEval.similar("", "alpha")).isEqualTo(0.0);
        assertThat(OutlineEval.similar("alpha", "")).isEqualTo(0.0);
    }

    // -- 单例指标 -------------------------------------------------------------

    @Test
    @DisplayName("生成与参考一致：三项指标均为 1，且在界内、无环")
    void identicalCaseScoresOne() {
        List<Map<String, Object>> modules = modules(kp("理解线程", "concept"));

        Map<String, Object> result = OutlineEval.evaluateCase(testCase("c1", modules, modules));

        assertThat(result).containsEntry("id", "c1")
                .containsEntry("kp_precision", 1.0)
                .containsEntry("kp_recall", 1.0)
                .containsEntry("kp_f1", 1.0)
                .containsEntry("count_in_bounds", true)
                .containsEntry("acyclic", true);
    }

    @Test
    @DisplayName("generated_modules 缺省时回落到 reference_modules")
    void generatedFallsBackToReference() {
        Map<String, Object> onlyReference = new LinkedHashMap<>();
        onlyReference.put("id", "c1");
        onlyReference.put("reference_modules", modules(kp("理解线程", "concept")));

        Map<String, Object> result = OutlineEval.evaluateCase(onlyReference);

        assertThat(result).containsEntry("kp_f1", 1.0).containsEntry("count_in_bounds", true);
    }

    @Test
    @DisplayName("空用例：precision/recall 默认 1.0，但 KP 数为 0 越界")
    void emptyCaseDefaultsToOne() {
        Map<String, Object> result = OutlineEval.evaluateCase(Map.of());

        assertThat(result).containsEntry("kp_precision", 1.0)
                .containsEntry("kp_recall", 1.0)
                .containsEntry("kp_f1", 1.0)
                .containsEntry("count_in_bounds", false)
                .containsEntry("acyclic", true);
    }

    @Test
    @DisplayName("count_in_bounds 是闭区间 [1, 60]")
    void countBoundsAreInclusive() {
        assertThat(OutlineEval.evaluateCase(testCase("c", modules(kp("a")), modules(kp("a"))))
                .get("count_in_bounds")).isEqualTo(true);
        assertThat(OutlineEval.evaluateCase(testCase("c",
                modules(kp("a")), modules(repeatKp(60)))).get("count_in_bounds")).isEqualTo(true);
        assertThat(OutlineEval.evaluateCase(testCase("c",
                modules(kp("a")), modules(repeatKp(61)))).get("count_in_bounds")).isEqualTo(false);
    }

    @Test
    @DisplayName("部分匹配：precision 1.0 / recall 0.5 / f1 2/3")
    void partialMatchComputesPrecisionAndRecall() {
        Map<String, Object> result = OutlineEval.evaluateCase(testCase("c",
                modules(kp("alpha"), kp("beta")),
                modules(kp("alpha"))));

        assertThat((Double) result.get("kp_precision")).isEqualTo(1.0);
        assertThat((Double) result.get("kp_recall")).isEqualTo(0.5);
        assertThat((Double) result.get("kp_f1")).isCloseTo(2.0 / 3.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("完全不相关：三项指标为 0")
    void unrelatedNamesScoreZero() {
        Map<String, Object> result = OutlineEval.evaluateCase(testCase("c",
                modules(kp("alpha")), modules(kp("完全不同的名字"))));

        assertThat((Double) result.get("kp_precision")).isEqualTo(0.0);
        assertThat((Double) result.get("kp_recall")).isEqualTo(0.0);
        assertThat((Double) result.get("kp_f1")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("贪心一对一：一个生成项只能命中一个参考项")
    void matchingIsGreedyOneToOne() {
        // 参考有 2 个同名项，生成只有 1 个 → 至多命中 1 个，recall = 1/2。
        List<Map<String, Object>> reference = modules(kp("alpha"), kp("alpha"));
        Map<String, Object> result = OutlineEval.evaluateCase(testCase("c",
                reference, modules(kp("alpha"))));

        assertThat((Double) result.get("kp_recall")).isEqualTo(0.5);
    }

    // -- 无环 -----------------------------------------------------------------

    @Test
    @DisplayName("前置成环 → acyclic=false")
    void cycleDetected() {
        List<Map<String, Object>> cyclic = modules(
                kp("alpha", List.of("beta")), kp("beta", List.of("alpha")));

        Map<String, Object> result = OutlineEval.evaluateCase(testCase("c", cyclic, cyclic));

        assertThat(result.get("acyclic")).isEqualTo(false);
    }

    @Test
    @DisplayName("前置指向不存在的节点时忽略该边")
    void missingPrerequisiteIgnored() {
        List<Map<String, Object>> modules = modules(kp("alpha", List.of("does-not-exist")));

        assertThat(OutlineEval.evaluateCase(testCase("c", modules, modules)).get("acyclic"))
                .isEqualTo(true);
    }

    // -- 汇总 -----------------------------------------------------------------

    @Test
    @DisplayName("空列表：只返回 4 个键，不含 mean_kp_precision / mean_kp_recall")
    void evaluateCasesEmptyShape() {
        Map<String, Object> report = OutlineEval.evaluateCases(List.of());

        assertThat(report).containsOnlyKeys(
                "cases", "mean_kp_f1", "all_acyclic", "all_in_bounds");
        assertThat(report).containsEntry("mean_kp_f1", 0.0)
                .containsEntry("all_acyclic", true)
                .containsEntry("all_in_bounds", true);
        assertThat((List<?>) report.get("cases")).isEmpty();
    }

    @Test
    @DisplayName("非空：补齐三个均值，all_* 取逻辑与")
    void evaluateCasesAverages() {
        List<Map<String, Object>> good = modules(kp("alpha"));
        Map<String, Object> report = OutlineEval.evaluateCases(List.of(
                testCase("c1", good, good),
                testCase("c2", good, modules(kp("beta")))));

        assertThat(report).containsKeys("mean_kp_precision", "mean_kp_recall", "mean_kp_f1");
        assertThat((Double) report.get("mean_kp_f1")).isCloseTo(0.5,
                org.assertj.core.data.Offset.offset(1e-9));
        assertThat(report.get("all_acyclic")).isEqualTo(true);
        assertThat(report.get("all_in_bounds")).isEqualTo(true);
        assertThat((List<?>) report.get("cases")).hasSize(2);
    }

    @Test
    @DisplayName("确定性：同一输入两次结果完全一致（TC-N6）")
    void evaluationIsDeterministic() {
        List<Map<String, Object>> cases = List.of(
                testCase("c1", modules(kp("alpha")), modules(kp("alpha"), kp("beta"))));

        assertThat(OutlineEval.evaluateCases(cases)).isEqualTo(OutlineEval.evaluateCases(cases));
    }

    // -- 加载 -----------------------------------------------------------------

    @Test
    @DisplayName("loadCases 跳过空行，保留每条 JSON")
    void loadCasesSkipsBlankLines() throws IOException {
        Path cases = tempDir.resolve("cases.jsonl");
        Files.writeString(cases,
                "{\"id\":\"c1\"}\n\n   \n{\"id\":\"c2\"}\n", StandardCharsets.UTF_8);

        List<Map<String, Object>> loaded = OutlineEval.loadCases(cases);

        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(0)).containsEntry("id", "c1");
        assertThat(loaded.get(1)).containsEntry("id", "c2");
    }

    // -- 夹具 -----------------------------------------------------------------

    private static Map<String, Object> testCase(String id, List<Map<String, Object>> reference,
                                               List<Map<String, Object>> generated) {
        Map<String, Object> testCase = new LinkedHashMap<>();
        testCase.put("id", id);
        testCase.put("reference_modules", reference);
        testCase.put("generated_modules", generated);
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

    private static Map<String, Object> kp(String name) {
        return kp(name, List.of());
    }

    private static Map<String, Object> kp(String name, String type) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("name", name);
        point.put("type", type);
        return point;
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
