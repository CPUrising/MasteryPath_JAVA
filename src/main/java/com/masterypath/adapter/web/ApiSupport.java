package com.masterypath.adapter.web;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 控制器共用的取值辅助。
 *
 * <p>与工具层的 {@code ToolSupport} 语义一致（复刻 Python 的宽松取值），但两者刻意不共用：
 * 工具层是包级可见的 {@code adapter.tool} 内部实现，控制器不应依赖它。
 */
final class ApiSupport {

    private ApiSupport() {
    }

    /** Python 的 {@code str(x or "")}。 */
    static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** Python 的 {@code bool(x)}：{@code None} / {@code False} / {@code 0} / {@code ""} / 空容器为假。 */
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
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    /** Python 的 {@code float(x)}；无法解析时返回 {@code fallback}。 */
    static double doubleOr(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.strip());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /** 构造保持插入顺序的 map；键值交替传入。 */
    static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }
}
