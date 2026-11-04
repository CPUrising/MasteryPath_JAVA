package com.masterypath.adapter.tool;

import com.masterypath.domain.mode.ModeGate;
import com.masterypath.domain.mode.SessionMode;

/**
 * 工具实现的公共辅助（参考实现 {@code tools.py} 的模块级函数）。
 *
 * <p>三个要点：
 * <ul>
 *   <li>{@link #wrongMode} 用<b>原始</b>模式值判定：未记录模式 → 不门控（{@code INV-09}）。</li>
 *   <li>{@link #repr} 复刻 Python 的 {@code repr(str)}：单引号包裹，供错误文案逐字对齐。</li>
 *   <li>{@link #str} / {@link #bool} / {@link #intOf} 复刻 Python 的宽松取值语义。</li>
 * </ul>
 */
final class ToolSupport {

    private ToolSupport() {
    }

    /**
     * 模式门控：不允许时返回拒绝结果，允许时返回 {@code null}。
     *
     * <p>用 {@link SessionMode#enforced} 语义（未记录 → 不门控），不是 {@code normalize}。
     */
    static ToolResult wrongMode(String toolName, ToolContext ctx) {
        if (ModeGate.toolIsAllowed(toolName, ctx.sessionMode())) {
            return null;
        }
        return ToolResult.failure(ModeGate.wrongModeMessage(toolName, ctx.sessionMode()));
    }

    /** Python 的 {@code repr(s)}：单引号包裹（不处理内部引号转义，参考实现文案里也没有）。 */
    static String repr(String text) {
        return "'" + (text == null ? "" : text) + "'";
    }

    /** Python 的 {@code str(x or "")}。 */
    static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** Python 的 {@code bool(x)}。 */
    static boolean bool(Object value) {
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
        if (value instanceof java.util.Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    /** Python 的 {@code int(x or 0)}：无法解析时归 0。 */
    static int intOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.strip());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /** Python 的 {@code str(x).strip()}。 */
    static String stripped(Object value) {
        return str(value).strip();
    }

    /** Python 的 {@code s[:limit]}。 */
    static String truncate(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    /** 读取字符串列表（{@code [str(x) for x in (raw or [])]}）。 */
    static java.util.List<String> stringList(Object raw) {
        java.util.List<String> values = new java.util.ArrayList<>();
        if (raw instanceof java.util.List<?> list) {
            for (Object item : list) {
                values.add(str(item));
            }
        }
        return values;
    }
}
