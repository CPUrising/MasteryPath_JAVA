package com.masterypath.adapter.web;

import com.masterypath.settings.ProviderProbe;
import com.masterypath.storage.SettingsStore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提供方探测端点（参考实现 {@code api/app.py} 408–467 行）。
 *
 * <p>四个端点全部<b>只读</b>：不写配置、不回显凭据。
 *
 * <p>「掩码回退」是本类的核心行为：编辑器提交的是屏幕上的值，
 * {@code api_key} 为空<b>且</b>给了 {@code provider_id} 时，改用已保存的 key，
 * 这样「测试连接」不必重输凭据。{@code protocol} / {@code base_url} 同理。
 */
@RestController
@RequestMapping("/settings")
public class SettingsProbeController {

    /** 探测函数的测试缝（参考实现靠 monkeypatch，Java 靠注入）。 */
    @FunctionalInterface
    public interface Probe {
        Map<String, Object> apply(String protocol, String baseUrl, String apiKey, String service);
    }

    /** 维度探测函数的测试缝。 */
    @FunctionalInterface
    public interface DimensionProbe {
        Map<String, Object> apply(String protocol, String baseUrl, String apiKey, String model);
    }

    private final SettingsStore store;
    private final Probe probe;
    private final Probe fetch;
    private final DimensionProbe dimension;

    @Autowired
    public SettingsProbeController(SettingsStore store) {
        this(store, null, null, null);
    }

    public SettingsProbeController(SettingsStore store, Probe probe, Probe fetch,
                                   DimensionProbe dimension) {
        this.store = store;
        this.probe = probe == null
                ? (protocol, baseUrl, apiKey, service) ->
                        ProviderProbe.probeProvider(protocol, baseUrl, apiKey, service, null)
                : probe;
        this.fetch = fetch == null
                ? (protocol, baseUrl, apiKey, service) ->
                        ProviderProbe.fetchModels(protocol, baseUrl, apiKey, service, null)
                : fetch;
        this.dimension = dimension == null
                ? (protocol, baseUrl, apiKey, model) ->
                        ProviderProbe.detectDimension(protocol, baseUrl, apiKey, model, null)
                : dimension;
    }

    @GetMapping("/providers/catalog")
    public List<Map<String, Object>> catalog() {
        return ProviderProbe.providerCatalog();
    }

    @PostMapping("/providers/probe")
    public Map<String, Object> probeProvider(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> fields = probeFields(payload);
        return probe.apply(str(fields.get("protocol")), str(fields.get("base_url")),
                fields.get("api_key") == null ? null : str(fields.get("api_key")),
                str(fields.get("service")));
    }

    @PostMapping("/providers/fetch-models")
    public Map<String, Object> fetchModels(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> fields = probeFields(payload);
        return fetch.apply(str(fields.get("protocol")), str(fields.get("base_url")),
                fields.get("api_key") == null ? null : str(fields.get("api_key")),
                str(fields.get("service")));
    }

    @PostMapping("/models/detect-dimension")
    public Map<String, Object> detectDimension(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        Map<String, Object> fields = probeFields(body);
        return dimension.apply(str(fields.get("protocol")), str(fields.get("base_url")),
                fields.get("api_key") == null ? null : str(fields.get("api_key")),
                str(body.get("model")));
    }

    // -- 内部 -----------------------------------------------------------------

    /**
     * 把提交的字段合并到已保存的提供方之上（参考实现 {@code _probe_fields}）。
     *
     * <p>只有 {@code api_key} 有「空则回退」的语义；{@code protocol} / {@code base_url}
     * 是「提交值 or 已保存 or 默认」，{@code service} 不回退。
     */
    private Map<String, Object> probeFields(Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        String providerId = str(body.get("provider_id"));
        Map<String, Object> saved = savedProvider(providerId);

        Object apiKey = body.get("api_key");
        if (!truthy(apiKey) && !providerId.isEmpty()) {
            apiKey = saved.get("api_key");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("protocol", firstNonEmpty(body.get("protocol"), saved.get("protocol"), "openai"));
        fields.put("base_url", firstNonEmpty(body.get("base_url"), saved.get("base_url"), ""));
        fields.put("api_key", apiKey);
        fields.put("service", str(body.get("service")));
        return fields;
    }

    private Map<String, Object> savedProvider(String providerId) {
        if (providerId.isEmpty()) {
            return Map.of();
        }
        Object raw = store.section("providers");
        if (!(raw instanceof List<?> list)) {
            return Map.of();
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> candidate
                    && providerId.equals(str(candidate.get("id")))) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : candidate.entrySet()) {
                    copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return copy;
            }
        }
        return Map.of();
    }

    /** {@code a or b or fallback}（Python 真值语义）。 */
    private static String firstNonEmpty(Object first, Object second, String fallback) {
        if (truthy(first)) {
            return str(first);
        }
        if (truthy(second)) {
            return str(second);
        }
        return fallback;
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

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
