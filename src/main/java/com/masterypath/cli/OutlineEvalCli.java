package com.masterypath.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.adapter.eval.OutlineEval;
import com.masterypath.domain.model.JsonSupport;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 大纲质量评测的命令行入口（参考实现 {@code eval/__main__.py}）。
 *
 * <pre>
 * java ... com.masterypath.cli.OutlineEvalCli outline --cases cases.jsonl [--baseline b.json] [--min-f1 0.8]
 * </pre>
 *
 * <p><b>退出码是 CI 契约</b>：指标低于阈值、前置图有环、KP 数越界、或相对基线退化
 * （超出 0.05 容差）都返回 {@code 1}，否则 {@code 0}。
 *
 * <p>报告进 {@code stdout}（缩进 JSON、不转义非 ASCII），失败原因进 {@code stderr}——
 * 与参考实现一致，避免把诊断信息混进可解析的 JSON。
 */
public final class OutlineEvalCli {

    /** 默认的 `--min-f1`。 */
    static final double MIN_F1 = 0.8;

    /** 基线退化的容差。 */
    static final double TOLERANCE = 0.05;

    private static final ObjectMapper MAPPER = JsonSupport.objectMapper();

    private OutlineEvalCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /** 等价于 Python 的 {@code main(argv)}：返回退出码而不是直接退出进程。 */
    public static int run(String[] args) {
        return run(args, System.out, System.err);
    }

    /**
     * 可注入输出的变体（测试用）。
     *
     * @param args    {@code outline --cases <path> [--baseline <path>] [--min-f1 <float>]}
     * @param out     JSON 报告
     * @param err     失败原因
     * @return 退出码
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        CliArgs parsed;
        try {
            parsed = CliArgs.parse(args);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 2;
        }
        if (parsed.help()) {
            out.println(CliArgs.USAGE);
            return 0;
        }
        if (!"outline".equals(parsed.command())) {
            err.println("unknown command: " + parsed.command());
            return 2;
        }
        if (parsed.cases() == null) {
            err.println("the following arguments are required: --cases");
            return 2;
        }

        List<Map<String, Object>> cases = OutlineEval.loadCases(Path.of(parsed.cases()));
        Map<String, Object> report = OutlineEval.evaluateCases(cases);
        out.println(pretty(report));

        boolean failed = false;
        double meanF1 = meanF1(report);
        if (meanF1 < parsed.minF1()) {
            err.println(String.format(Locale.ROOT, "FAIL: mean_kp_f1 %.3f < %s",
                    meanF1, format(parsed.minF1())));
            failed = true;
        }
        if (!Boolean.TRUE.equals(report.get("all_acyclic"))) {
            err.println("FAIL: prerequisite cycle detected");
            failed = true;
        }
        if (!Boolean.TRUE.equals(report.get("all_in_bounds"))) {
            err.println("FAIL: knowledge-point count out of bounds");
            failed = true;
        }
        if (parsed.baseline() != null) {
            double baseline = baselineF1(Path.of(parsed.baseline()));
            if (meanF1 < baseline - TOLERANCE) {
                err.println("FAIL: regression vs baseline");
                failed = true;
            }
        }
        return failed ? 1 : 0;
    }

    // -- 内部 -----------------------------------------------------------------

    /** 基线里的 {@code mean_kp_f1}；缺键或读不了都按 {@code 0.0}（参考实现同款）。 */
    private static double baselineF1(Path path) {
        try {
            Object parsed = MAPPER.readValue(Files.readString(path, StandardCharsets.UTF_8),
                    Object.class);
            if (parsed instanceof Map<?, ?> map && map.get("mean_kp_f1") instanceof Number number) {
                return number.doubleValue();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read baseline: " + path, e);
        }
        return 0.0;
    }

    private static double meanF1(Map<String, Object> report) {
        Object value = report.get("mean_kp_f1");
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    /** {: 去掉小数点后多余的 0}，用于 `FAIL: ... < 0.8` 这类文案。 */
    private static String format(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** 缩进 JSON，且不把中文转义成 {@code \\uXXXX}（对应 `ensure_ascii=False`）。 */
    static String pretty(Map<String, Object> report) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        } catch (IOException e) {
            throw new IllegalStateException("cannot serialize report", e);
        }
    }

    /** 极简参数解析：只支持 `outline` 子命令与三个选项。 */
    record CliArgs(String command, String cases, String baseline, double minF1, boolean help) {

        static final String USAGE = "usage: masterypath-eval outline --cases CASES "
                + "[--baseline BASELINE] [--min-f1 MIN_F1]";

        static CliArgs parse(String[] args) {
            String command = "";
            String cases = null;
            String baseline = null;
            double minF1 = MIN_F1;
            boolean help = false;

            List<String> tokens = args == null ? List.of() : List.of(args);
            for (int index = 0; index < tokens.size(); index++) {
                String token = tokens.get(index);
                if (token == null || token.isEmpty()) {
                    continue;
                }
                if ("-h".equals(token) || "--help".equals(token)) {
                    help = true;
                    continue;
                }
                if ("--cases".equals(token)) {
                    cases = valueOf(tokens, ++index, "--cases");
                    continue;
                }
                if ("--baseline".equals(token)) {
                    baseline = valueOf(tokens, ++index, "--baseline");
                    continue;
                }
                if ("--min-f1".equals(token)) {
                    minF1 = parseDouble(valueOf(tokens, ++index, "--min-f1"));
                    continue;
                }
                if (token.startsWith("--")) {
                    throw new IllegalArgumentException("unrecognized argument: " + token);
                }
                if (command.isEmpty()) {
                    command = token;
                } else {
                    throw new IllegalArgumentException("unrecognized argument: " + token);
                }
            }
            return new CliArgs(command, cases, baseline, minF1, help);
        }

        private static String valueOf(List<String> tokens, int index, String flag) {
            if (index >= tokens.size()) {
                throw new IllegalArgumentException("argument " + flag + ": expected one value");
            }
            return tokens.get(index);
        }

        private static double parseDouble(String raw) {
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("argument --min-f1: invalid float value: " + raw);
            }
        }
    }
}
