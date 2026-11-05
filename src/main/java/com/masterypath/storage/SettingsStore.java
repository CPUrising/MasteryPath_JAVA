package com.masterypath.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设置存储（参考实现 {@code settings/store.py}）。
 *
 * <p>一个 workspace 一个 {@code settings.json}，六个分组：{@code providers} / {@code llm} /
 * {@code task-models} / {@code embedding} / {@code general} / {@code indexing}。
 *
 * <p>三条归一规则不可省：
 * <ul>
 *   <li>模型分组恒为 {@code {models: [...], active_id: ""}}；旧式单条 {@code {provider, model}}
 *       折叠成一条列表，否则升级后配置丢失。</li>
 *   <li>提供方 {@code protocol} 不在白名单时回退 {@code openai}；非字典的 {@code detection} 丢弃。</li>
 *   <li>读取时把默认值与文件内容<b>浅合并</b>：分组是字典则逐键覆盖，否则整体替换。</li>
 * </ul>
 *
 * <p>本类不做提供方探测（属 {@code 0022}），只负责读写与归一。
 */
public class SettingsStore {

    /** 六个分组（顺序即默认值声明顺序）。 */
    public static final List<String> SECTIONS = List.of(
            "providers", "llm", "task-models", "embedding", "general", "indexing");

    /** 形如 {@code {models: [...], active_id: ""}} 的分组。 */
    public static final List<String> MODEL_SECTIONS = List.of("llm", "task-models", "embedding");

    /** 提供方可用的协议。 */
    public static final List<String> PROVIDER_PROTOCOLS =
            List.of("openai", "anthropic", "ollama", "custom");

    private final Path baseDir;
    private final Path path;
    private final ObjectMapper mapper = JsonSupport.objectMapper();
    private final Object lock = new Object();

    public SettingsStore(Path baseDir) {
        this.baseDir = baseDir;
        this.path = baseDir.resolve("settings.json");
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("cannot create settings dir at " + baseDir, e);
        }
    }

    /** 配置文件路径（测试用于断言落盘）。 */
    public Path path() {
        return path;
    }

    // -- 读 -------------------------------------------------------------------

    /** 读取全部分组（默认值 + 文件内容合并 + 归一）。 */
    public Map<String, Object> read() {
        synchronized (lock) {
            Map<String, Object> merged = defaults();
            if (Files.exists(path)) {
                Object parsed;
                try {
                    parsed = mapper.readValue(Files.readString(path, StandardCharsets.UTF_8),
                            Map.class);
                } catch (IOException e) {
                    // 坏文件等价于空文件（参考实现同样吞掉 ValueError/OSError）。
                    parsed = Map.of();
                }
                if (parsed instanceof Map<?, ?> data) {
                    for (Map.Entry<?, ?> entry : data.entrySet()) {
                        String key = String.valueOf(entry.getKey());
                        Object current = merged.get(key);
                        if (current instanceof Map<?, ?> currentMap
                                && entry.getValue() instanceof Map<?, ?> incoming) {
                            Map<String, Object> combined = castMap(currentMap);
                            combined.putAll(castMap(incoming));
                            merged.put(key, combined);
                        } else {
                            merged.put(key, entry.getValue());
                        }
                    }
                }
            }
            for (String name : MODEL_SECTIONS) {
                merged.put(name, normalizeModelSection(merged.get(name)));
            }
            merged.put("providers", normalizeProviders(merged.get("providers")));
            return merged;
        }
    }

    /**
     * 读一个分组。
     *
     * @throws UnknownSection 分组名不在 {@link #SECTIONS} 里
     */
    public Object section(String name) {
        requireKnown(name);
        return read().get(name);
    }

    // -- 写 -------------------------------------------------------------------

    /**
     * 写一个分组并落盘。
     *
     * @return 归一后的分组值
     * @throws UnknownSection 分组名不在 {@link #SECTIONS} 里
     */
    public Object writeSection(String name, Object value) {
        requireKnown(name);
        Object normalized = value;
        if (MODEL_SECTIONS.contains(name)) {
            normalized = normalizeModelSection(value);
        } else if ("providers".equals(name)) {
            normalized = normalizeProviders(value);
        }
        synchronized (lock) {
            Map<String, Object> data = read();
            data.put(name, normalized);
            try {
                Files.writeString(path, mapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(data), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("cannot write settings to " + path, e);
            }
            return data.get(name);
        }
    }

    // -- 归一 -----------------------------------------------------------------

    /** 默认设置（每次返回新对象，避免调用方改到共享状态）。 */
    public static Map<String, Object> defaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("providers", new ArrayList<>());
        defaults.put("llm", emptyModelSection());
        defaults.put("task-models", emptyModelSection());
        defaults.put("embedding", emptyModelSection());
        defaults.put("general", payload("interface_language", "zh", "response_language", "zh"));
        defaults.put("indexing", payload("chunk_size", 512, "chunk_overlap", 64));
        return defaults;
    }

    private static Map<String, Object> emptyModelSection() {
        return payload("models", new ArrayList<>(), "active_id", "");
    }

    /** 归一模型分组；旧式单条 {@code {provider, model}} 折叠成一条列表。 */
    static Map<String, Object> normalizeModelSection(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return emptyModelSection();
        }
        Object rawModels = map.get("models");
        List<Object> models = new ArrayList<>();
        if (rawModels instanceof List<?> list) {
            for (Object item : list) {
                models.add(normalizeModelEntry(item));
            }
        } else {
            String legacyModel = str(map.get("model")).strip();
            if (!legacyModel.isEmpty()) {
                Object providerId = map.containsKey("provider_id")
                        ? map.get("provider_id") : map.get("provider");
                models.add(payload(
                        "id", "m1",
                        "provider_id", str(providerId),
                        "model", legacyModel,
                        "name", legacyModel));
            }
        }
        return payload("models", models, "active_id", str(map.get("active_id")));
    }

    /** 模型条目：{@code services} 只保留非空字符串，非列表则丢弃该键。 */
    private static Object normalizeModelEntry(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return raw;
        }
        Map<String, Object> entry = new LinkedHashMap<>(castMap(map));
        Object services = entry.get("services");
        if (services instanceof List<?> list) {
            List<String> kept = new ArrayList<>();
            for (Object item : list) {
                if (truthy(item)) {
                    kept.add(str(item));
                }
            }
            entry.put("services", kept);
        } else {
            entry.remove("services");
        }
        return entry;
    }

    /** 提供方列表归一：非列表 → 空列表。 */
    static List<Object> normalizeProviders(Object raw) {
        List<Object> providers = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                providers.add(normalizeProvider(item));
            }
        }
        return providers;
    }

    /** 单个提供方：{@code protocol} 白名单回退 {@code openai}；非字典 {@code detection} 丢弃。 */
    private static Object normalizeProvider(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return raw;
        }
        Map<String, Object> provider = new LinkedHashMap<>(castMap(map));
        String protocol = str(provider.get("protocol")).strip().toLowerCase();
        provider.put("protocol", PROVIDER_PROTOCOLS.contains(protocol) ? protocol : "openai");
        if (!(provider.get("detection") instanceof Map<?, ?>)) {
            provider.remove("detection");
        }
        return provider;
    }

    // -- 辅助 -----------------------------------------------------------------

    private static void requireKnown(String name) {
        if (!SECTIONS.contains(name)) {
            throw new UnknownSection(name);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** Python 的真值语义：{@code null} / 空串 / 0 / 空容器为假。 */
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

    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }

    /** 未知分组（参考实现 {@code UnknownSection}）。 */
    public static class UnknownSection extends IllegalArgumentException {
        public UnknownSection(String name) {
            super("unknown settings section: " + name);
        }
    }
}
