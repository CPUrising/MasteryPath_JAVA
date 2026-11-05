package com.masterypath.adapter.llm;

import com.masterypath.storage.SettingsStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模型解析（参考实现 {@code agents/model_resolution.py}）。
 *
 * <p>三条失败文案逐字锁定：选中模型已不存在 / 没有模型 / 提供方缺 base URL。
 */
class ModelResolutionTest {

    @TempDir
    Path tempDir;

    private SettingsStore settings;

    @BeforeEach
    void setUp() {
        settings = new SettingsStore(tempDir);
    }

    @Test
    @DisplayName("normalizeSelection：无 model_id 返回 null")
    void normalizeSelectionRequiresModelId() {
        assertThat(ModelResolution.normalizeSelection(null)).isNull();
        assertThat(ModelResolution.normalizeSelection("junk")).isNull();
        assertThat(ModelResolution.normalizeSelection(Map.of("provider_id", "p"))).isNull();
        assertThat(ModelResolution.normalizeSelection(Map.of("model_id", "  "))).isNull();
        assertThat(ModelResolution.normalizeSelection(Map.of("model_id", "m1")))
                .isEqualTo(new ModelResolution.Selection("", "m1"));
    }

    @Test
    @DisplayName("按 active_id 解析出完整配置")
    void resolvesActiveModel() {
        configure();

        Map<String, Object> config = ModelResolution.resolveLlmConfig(settings, null);

        assertThat(config).containsEntry("provider_id", "fake")
                .containsEntry("model_id", "m1")
                .containsEntry("model", "fake-model")
                .containsEntry("protocol", "openai")
                .containsEntry("base_url", "http://127.0.0.1:1/v1")
                .containsEntry("name", "Fake");
    }

    @Test
    @DisplayName("按显式选择解析")
    void resolvesExplicitSelection() {
        configure();

        Map<String, Object> config = ModelResolution.resolveLlmConfig(settings,
                new ModelResolution.Selection("fake", "m1"));

        assertThat(config).containsEntry("model_id", "m1");
    }

    @Test
    @DisplayName("active_id 缺失时退回第一条模型")
    void fallsBackToFirstModel() {
        settings.writeSection("providers", List.of(provider()));
        settings.writeSection("llm", Map.of(
                "models", List.of(model("m9", "only")),
                "active_id", ""));

        assertThat(ModelResolution.resolveLlmConfig(settings, null))
                .containsEntry("model_id", "m9");
    }

    @Test
    @DisplayName("选中的模型不存在 → bad_request")
    void unknownSelectionIsBadRequest() {
        configure();

        assertThatThrownBy(() -> ModelResolution.resolveLlmConfig(settings,
                Map.of("model_id", "ghost")))
                .isInstanceOf(LlmError.class)
                .hasMessageContaining("no longer configured")
                .satisfies(error -> {
                    LlmError llmError = (LlmError) error;
                    assertThat(llmError.code()).isEqualTo("bad_request");
                    assertThat(llmError.retryable()).isFalse();
                });
    }

    @Test
    @DisplayName("一个模型都没配 → bad_request，文案含 No language model")
    void noModelConfigured() {
        assertThatThrownBy(() -> ModelResolution.resolveLlmConfig(settings, null))
                .isInstanceOf(LlmError.class)
                .hasMessageContaining("No language model");
    }

    @Test
    @DisplayName("提供方缺 base URL → bad_request")
    void providerWithoutBaseUrl() {
        settings.writeSection("providers", List.of(Map.of("id", "fake", "name", "Fake")));
        settings.writeSection("llm", Map.of(
                "models", List.of(model("m1", "fake-model")),
                "active_id", "m1"));

        assertThatThrownBy(() -> ModelResolution.resolveLlmConfig(settings, null))
                .isInstanceOf(LlmError.class)
                .hasMessageContaining("missing a base URL");
    }

    @Test
    @DisplayName("buildClient 把配置转成客户端（不抛错即视为成功）")
    void buildClientFromConfig() {
        configure();
        Map<String, Object> config = ModelResolution.resolveLlmConfig(settings, null);

        OpenAiCompatibleClient client = ModelResolution.buildClient(config);

        assertThat(client).isNotNull();
    }

    // -- 辅助 -----------------------------------------------------------------

    private void configure() {
        settings.writeSection("providers", List.of(provider()));
        settings.writeSection("llm", Map.of(
                "models", List.of(model("m1", "fake-model")),
                "active_id", "m1"));
    }

    private static Map<String, Object> provider() {
        return Map.of(
                "id", "fake",
                "name", "Fake",
                "base_url", "http://127.0.0.1:1/v1",
                "api_key", "",
                "protocol", "openai");
    }

    private static Map<String, Object> model(String id, String model) {
        return Map.of("id", id, "provider_id", "fake", "model", model, "name", "Fake");
    }
}
