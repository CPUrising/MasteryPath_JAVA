package com.masterypath.adapter.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设置分组读写（参考实现 {@code settings/store.py} + {@code api/app.py} 390–406 行）。
 *
 * <p><b>本 change 只做内存实现</b>：分组语义（默认值、模型分组归一、提供方归一）逐字复刻，
 * 但落盘（{@code settings.json}）与提供方探测属 {@code 0021-settings-task-model}。
 *
 * <p>归一规则不可省：
 * <ul>
 *   <li>模型分组恒为 {@code {models: [...], active_id: ""}}；旧式单条 {@code {provider, model}}
 *       折叠成一条列表，否则升级后配置丢失。</li>
 *   <li>提供方 {@code protocol} 不在白名单时回退 {@code openai}；非字典的 {@code detection} 丢弃。</li>
 * </ul>
 */
@RestController
@RequestMapping("/settings")
public class SettingsController {

    /** 分组名（顺序即 {@code SECTIONS}）。 */
    private static final List<String> SECTIONS = List.of(
            "providers", "llm", "task-models", "embedding", "general", "indexing");

    /** 形如 {@code {models: [...], active_id: ""}} 的分组。 */
    private static final List<String> MODEL_SECTIONS = List.of("llm", "task-models", "embedding");

    /** 提供方可用的协议。 */
    private static final List<String> PROVIDER_PROTOCOLS =
            List.of("openai", "anthropic", "ollama", "custom");

    /** 已写入的分组（内存态；落盘属 0021）。 */
    private final Map<String, Object> stored = new LinkedHashMap<>();

    @GetMapping
    public Map<String, Object> read() {
        Map<String, Object> merged = defaults();
        for (Map.Entry<String, Object> entry : stored.entrySet()) {
            Object current = merged.get(entry.getKey());
            if (current instanceof Map<?, ?> currentMap && entry.getValue() instanceof Map<?, ?> incoming) {
                Map<String, Object> combined = new LinkedHashMap<>(castMap(currentMap));
                combined.putAll(castMap(incoming));
                merged.put(entry.getKey(), combined);
            } else {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        for (String name : MODEL_SECTIONS) {
            merged.put(name, normalizeModelSection(merged.get(name)));
        }
        merged.put("providers", normalizeProviders(merged.get("providers")));
        return merged;
    }

    @GetMapping("/{section}")
    public Object section(@PathVariable String section) {
        requireKnown(section);
        return read().get(section);
    }

    @PutMapping("/{section}")
    public Object writeSection(@PathVariable String section,
                               @RequestBody(required = false) Object payload) {
        requireKnown(section);
        Object value = payload;
        if (MODEL_SECTIONS.contains(section)) {
            value = normalizeModelSection(payload);
        } else if ("providers".equals(section)) {
            value = normalizeProviders(payload);
        }
        stored.put(section, value);
        return read().get(section);
    }

    // -- 辅助 -----------------------------------------------------------------

    private static void requireKnown(String section) {
        if (!SECTIONS.contains(section)) {
            throw new ApiException(404, "unknown settings section: " + section);
        }
    }

    /** 默认设置（每次返回新对象，避免调用方改到共享状态）。 */
    private static Map<String, Object> defaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("providers", new ArrayList<>());
        defaults.put("llm", emptyModelSection());
        defaults.put("task-models", emptyModelSection());
        defaults.put("embedding", emptyModelSection());
        defaults.put("general", ApiSupport.payload(
                "interface_language", "zh", "response_language", "zh"));
        defaults.put("indexing", ApiSupport.payload("chunk_size", 512, "chunk_overlap", 64));
        return defaults;
    }

    private static Map<String, Object> emptyModelSection() {
        return ApiSupport.payload("models", new ArrayList<>(), "active_id", "");
    }

    /** 归一模型分组；旧式单条 {@code {provider, model}} 折叠成一条列表。 */
    private static Map<String, Object> normalizeModelSection(Object raw) {
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
            String legacyModel = ApiSupport.str(map.get("model")).strip();
            if (!legacyModel.isEmpty()) {
                Object providerId = map.containsKey("provider_id")
                        ? map.get("provider_id") : map.get("provider");
                models.add(ApiSupport.payload(
                        "id", "m1",
                        "provider_id", ApiSupport.str(providerId),
                        "model", legacyModel,
                        "name", legacyModel));
            }
        }
        return ApiSupport.payload("models", models,
                "active_id", ApiSupport.str(map.get("active_id")));
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
                if (ApiSupport.bool(item)) {
                    kept.add(ApiSupport.str(item));
                }
            }
            entry.put("services", kept);
        } else {
            entry.remove("services");
        }
        return entry;
    }

    /** 提供方列表归一：非列表 → 空列表。 */
    private static List<Object> normalizeProviders(Object raw) {
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
        String protocol = ApiSupport.str(provider.get("protocol")).strip().toLowerCase();
        provider.put("protocol", PROVIDER_PROTOCOLS.contains(protocol) ? protocol : "openai");
        if (!(provider.get("detection") instanceof Map<?, ?>)) {
            provider.remove("detection");
        }
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
