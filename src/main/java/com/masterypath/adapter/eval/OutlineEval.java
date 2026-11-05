package com.masterypath.adapter.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 大纲质量的离线评测指标（参考实现 {@code eval/outline_eval.py}，{@code ADR-018} / {@code spec 09}）。
 *
 * <p><b>全部确定性、全部离线</b>：不调模型、不发请求。语义指标用「KP 名称的令牌重叠」
 * 作代理（阈值 0.8），替换成嵌入匹配时用例格式不变。
 *
 * <p>指标含义：
 * <ul>
 *   <li>{@code kp_precision} / {@code kp_recall} / {@code kp_f1}：生成 KP 与参考 KP 的匹配比例；</li>
 *   <li>{@code count_in_bounds}：生成 KP 数在闭区间 {@code [1, 60]}；</li>
 *   <li>{@code acyclic}：生成（或回落参考）大纲的前置图无环。</li>
 * </ul>
 */
public final class OutlineEval {

    /** 令牌：Python {@code [\w\u4e00-\u9fff]+} 的等价写法（字母 / 数字 / 下划线）。 */
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}_]+");

    /** 两个 KP 名判为「同一知识点」的相似度门槛。 */
    static final double MATCH_THRESHOLD = 0.8;

    /** KP 数的合法区间（闭区间）。 */
    static final int MIN_COUNT = 1;
    static final int MAX_COUNT = 60;

    private static final ObjectMapper MAPPER = JsonSupport.objectMapper();

    private OutlineEval() {
    }

    // -- 单例 -----------------------------------------------------------------

    /**
     * 给一个用例打分。
     *
     * <p>{@code generated_modules} 缺省时回落到 {@code reference_modules}——
     * 「只给参考大纲」按「生成得完全正确」计。
     *
     * @param testCase 含 {@code id} / {@code reference_modules} / {@code generated_modules}
     * @return 5 个指标键 + {@code id}
     */
    public static Map<String, Object> evaluateCase(Map<String, Object> testCase) {
        Map<String, Object> safe = testCase == null ? Map.of() : testCase;
        List<String> reference = kpNames(modulesOf(safe.get("reference_modules")));
        List<String> generated = kpNames(modulesOf(firstTruthy(
                safe.get("generated_modules"), safe.get("reference_modules"))));

        int matched = 0;
        Set<Integer> used = new HashSet<>();
        for (String name : generated) {
            for (int index = 0; index < reference.size(); index++) {
                if (used.contains(index)) {
                    continue;
                }
                if (similar(name, reference.get(index)) >= MATCH_THRESHOLD) {
                    used.add(index);
                    matched++;
                    break;
                }
            }
        }
        double precision = generated.isEmpty() ? 1.0 : (double) matched / generated.size();
        double recall = reference.isEmpty() ? 1.0 : (double) matched / reference.size();
        double f1 = (precision + recall) == 0.0
                ? 0.0 : 2 * precision * recall / (precision + recall);
        int count = generated.size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", str(safe.get("id")));
        result.put("kp_precision", precision);
        result.put("kp_recall", recall);
        result.put("kp_f1", f1);
        result.put("count_in_bounds", count >= MIN_COUNT && count <= MAX_COUNT);
        result.put("acyclic", acyclic(modulesOf(firstTruthy(
                safe.get("generated_modules"), safe.get("reference_modules")))));
        return result;
    }

    // -- 汇总 -----------------------------------------------------------------

    /**
     * 汇总一批用例。
     *
     * <p><b>空列表的形状与参考实现一致</b>：只返回 {@code cases} / {@code mean_kp_f1} /
     * {@code all_acyclic} / {@code all_in_bounds}，<b>不含</b>
     * {@code mean_kp_precision} / {@code mean_kp_recall}。非空才补齐。
     */
    public static Map<String, Object> evaluateCases(List<Map<String, Object>> cases) {
        List<Map<String, Object>> perCase = new ArrayList<>();
        if (cases != null) {
            for (Map<String, Object> testCase : cases) {
                perCase.add(evaluateCase(testCase));
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("cases", perCase);
        if (perCase.isEmpty()) {
            report.put("mean_kp_f1", 0.0);
            report.put("all_acyclic", true);
            report.put("all_in_bounds", true);
            return report;
        }
        report.put("mean_kp_precision", mean(perCase, "kp_precision"));
        report.put("mean_kp_recall", mean(perCase, "kp_recall"));
        report.put("mean_kp_f1", mean(perCase, "kp_f1"));
        report.put("all_acyclic", allTrue(perCase, "acyclic"));
        report.put("all_in_bounds", allTrue(perCase, "count_in_bounds"));
        return report;
    }

    /**
     * 读取 JSONL：每行一个 JSON 对象，空行跳过。
     *
     * @throws UncheckedIOException 文件读不了或某行不是合法 JSON
     */
    public static List<Map<String, Object>> loadCases(Path path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read cases file: " + path, e);
        }
        List<Map<String, Object>> cases = new ArrayList<>();
        for (String line : lines) {
            if (line.strip().isEmpty()) {
                continue;
            }
            try {
                Object parsed = MAPPER.readValue(line, Object.class);
                if (parsed instanceof Map<?, ?> map) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> item : map.entrySet()) {
                        entry.put(String.valueOf(item.getKey()), item.getValue());
                    }
                    cases.add(entry);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("invalid JSON line in " + path, e);
            }
        }
        return cases;
    }

    // -- 内部 -----------------------------------------------------------------

    /** 分词并去重（小写化）。 */
    static Set<String> tokens(String name) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(str(name).toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }

    /** Jaccard 相似度；任一为空返回 {@code 0.0}。 */
    static double similar(String a, String b) {
        Set<String> left = tokens(a);
        Set<String> right = tokens(b);
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        return (double) intersection.size() / union.size();
    }

    /** 所有模块下的 KP 名（保持出现顺序）。 */
    static List<String> kpNames(List<Map<String, Object>> modules) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> module : modules) {
            for (Map<String, Object> point : mapsOf(module.get("knowledge_points"))) {
                names.add(str(point.get("name")));
            }
        }
        return names;
    }

    /**
     * 前置图是否无环：DFS 三色标记，只遍历图里存在的节点。
     *
     * <p>前置指向不存在的节点时该边被忽略（参考实现 `if neighbour in graph`）。
     */
    static boolean acyclic(List<Map<String, Object>> modules) {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (Map<String, Object> module : modules) {
            for (Map<String, Object> point : mapsOf(module.get("knowledge_points"))) {
                List<String> prerequisites = new ArrayList<>();
                if (point.get("prerequisites") instanceof List<?> list) {
                    for (Object item : list) {
                        prerequisites.add(str(item));
                    }
                }
                graph.put(str(point.get("name")), prerequisites);
            }
        }
        Map<String, Integer> state = new LinkedHashMap<>();
        for (String node : graph.keySet()) {
            if (!visit(node, graph, state)) {
                return false;
            }
        }
        return true;
    }

    private static boolean visit(String node, Map<String, List<String>> graph,
                                 Map<String, Integer> state) {
        Integer marker = state.get(node);
        if (marker != null && marker == 1) {
            return false;
        }
        if (marker != null && marker == 2) {
            return true;
        }
        state.put(node, 1);
        for (String neighbour : graph.getOrDefault(node, List.of())) {
            if (graph.containsKey(neighbour) && !visit(neighbour, graph, state)) {
                return false;
            }
        }
        state.put(node, 2);
        return true;
    }

    private static double mean(List<Map<String, Object>> rows, String key) {
        double total = 0.0;
        for (Map<String, Object> row : rows) {
            total += number(row.get(key));
        }
        return total / rows.size();
    }

    private static boolean allTrue(List<Map<String, Object>> rows, String key) {
        for (Map<String, Object> row : rows) {
            if (!Boolean.TRUE.equals(row.get(key))) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> modulesOf(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> modules = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                modules.add(copy);
            }
        }
        return modules;
    }

    private static List<Map<String, Object>> mapsOf(Object value) {
        return modulesOf(value);
    }

    /** Python 的 {@code a or b}。 */
    private static Object firstTruthy(Object first, Object second) {
        return truthy(first) ? first : second;
    }

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
        if (value instanceof java.util.Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
