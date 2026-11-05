package com.masterypath.adapter.llm;

import com.masterypath.storage.SettingsStore;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「设置 + 可选会话选择 → 具体客户端」的唯一入口（参考实现 {@code agents/model_resolution.py}）。
 *
 * <p>普通聊天与掌握式导师都经这里解析模型，因此「我在跟哪个模型说话」只有一个答案。
 * 选择用 {@code {provider_id, model_id}} 寻址：裸 {@code model_id} 可能跨提供方重复。
 *
 * <p>三条失败文案逐字复刻（都是 {@code bad_request}，不可重试）：
 * <ul>
 *   <li>选中的模型已不存在 → {@code "The selected model is no longer configured. ..."}</li>
 *   <li>一个模型都没配 → {@code "No language model is configured. ..."}</li>
 *   <li>提供方缺 base URL → {@code "The model's provider is missing a base URL. ..."}</li>
 * </ul>
 */
public final class ModelResolution {

    private ModelResolution() {
    }

    /**
     * 规范化一次模型选择。
     *
     * @param value 形如 {@code {provider_id, model_id}}；可为 {@code null} 或任意类型
     * @return 未指明 {@code model_id} 时返回 {@code null}
     */
    public static Selection normalizeSelection(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        String modelId = str(map.get("model_id")).strip();
        if (modelId.isEmpty()) {
            return null;
        }
        return new Selection(str(map.get("provider_id")).strip(), modelId);
    }

    /**
     * 把选择（或默认活动模型）解析成客户端配置。
     *
     * @param settings  设置存储
     * @param selection {@link Selection}、{@code {provider_id, model_id}} 字典，或 {@code null}（用默认）
     * @return 可直接交给 {@link #buildClient} 的配置
     * @throws LlmError 选中的模型不存在、没有可用模型，或提供方缺 base URL
     */
    public static Map<String, Object> resolveLlmConfig(SettingsStore settings, Object selection) {
        Selection wanted = selection instanceof Selection chosen
                ? chosen : normalizeSelection(selection);

        Object rawSection = settings.section("llm");
        Map<String, Object> section = rawSection instanceof Map<?, ?> map
                ? castMap(map) : Map.of();
        List<?> models = section.get("models") instanceof List<?> list ? list : List.of();

        Map<String, Object> chosen = null;
        if (wanted != null) {
            for (Object item : models) {
                if (item instanceof Map<?, ?> model
                        && wanted.modelId().equals(str(model.get("id")))
                        && (wanted.providerId().isEmpty()
                        || wanted.providerId().equals(str(model.get("provider_id"))))) {
                    chosen = castMap(model);
                    break;
                }
            }
            if (chosen == null) {
                throw notConfigured("The selected model is no longer configured. "
                        + "Pick another under Settings → Language models.");
            }
        }
        if (chosen == null) {
            String active = str(section.get("active_id"));
            for (Object item : models) {
                if (item instanceof Map<?, ?> model && active.equals(str(model.get("id")))) {
                    chosen = castMap(model);
                    break;
                }
            }
        }
        if (chosen == null && !models.isEmpty() && models.get(0) instanceof Map<?, ?> first) {
            chosen = castMap(first);
        }
        if (chosen == null || str(chosen.get("model")).strip().isEmpty()) {
            throw notConfigured(
                    "No language model is configured. Add one under Settings → Language models.");
        }

        String providerId = str(chosen.get("provider_id"));
        Object rawProviders = settings.section("providers");
        Map<String, Object> provider = null;
        if (rawProviders instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> candidate
                        && providerId.equals(str(candidate.get("id")))) {
                    provider = castMap(candidate);
                    break;
                }
            }
        }
        if (provider == null || str(provider.get("base_url")).strip().isEmpty()) {
            throw notConfigured(
                    "The model's provider is missing a base URL. Check Settings → Providers.");
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("provider_id", providerId);
        config.put("model_id", str(chosen.get("id")));
        config.put("name", truthy(chosen.get("name"))
                ? str(chosen.get("name")) : str(chosen.get("model")));
        config.put("model", str(chosen.get("model")));
        config.put("protocol", truthy(provider.get("protocol"))
                ? str(provider.get("protocol")) : "openai");
        config.put("base_url", str(provider.get("base_url")));
        config.put("api_key", provider.get("api_key") == null ? "" : provider.get("api_key"));
        config.put("extra_headers", provider.get("extra_headers") instanceof Map<?, ?> headers
                ? castMap(headers) : new LinkedHashMap<>());
        return config;
    }

    /** 从解析结果构造客户端（参考实现 {@code build_client}）。 */
    public static OpenAiCompatibleClient buildClient(Map<String, Object> config) {
        return new OpenAiCompatibleClient(
                str(config.get("base_url")),
                str(config.get("api_key")),
                str(config.get("model")),
                truthy(config.get("protocol")) ? str(config.get("protocol")) : "openai",
                Duration.ofSeconds(120),
                stringMap(config.get("extra_headers")),
                str(config.get("session_key")),
                null);
    }

    // -- 辅助 -----------------------------------------------------------------

    private static LlmError notConfigured(String message) {
        return new LlmError(message, "bad_request", false);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    private static Map<String, String> stringMap(Object raw) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                headers.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return headers;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
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

    /**
     * 一次模型选择（参考实现 {@code LLMSelection}）。
     *
     * @param providerId 提供方 id，可为空串（表示不限）
     * @param modelId    模型 id，非空
     */
    public record Selection(String providerId, String modelId) {
        public Selection {
            providerId = providerId == null ? "" : providerId;
            modelId = modelId == null ? "" : modelId;
        }
    }
}
