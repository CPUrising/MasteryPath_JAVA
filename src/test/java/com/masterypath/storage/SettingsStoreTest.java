package com.masterypath.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 设置存储（参考实现 {@code settings/store.py}）。
 *
 * <p>本 change 把 `0018` 的内存实现换成落盘；分组语义与归一规则不变
 * （{@code SettingsApiTest} 的断言可原样通过）。
 */
@Tag("storage")
class SettingsStoreTest {

    @TempDir
    Path tempDir;

    private SettingsStore store;

    @BeforeEach
    void setUp() {
        store = new SettingsStore(tempDir);
    }

    @Test
    @DisplayName("默认值与参考实现一致")
    void defaultsMatchReference() {
        Map<String, Object> all = store.read();

        assertThat(all).containsKeys("providers", "llm", "task-models", "embedding",
                "general", "indexing");
        assertThat(all.get("llm")).isEqualTo(Map.of("models", List.of(), "active_id", ""));
        assertThat(all.get("indexing")).isEqualTo(Map.of("chunk_size", 512, "chunk_overlap", 64));
    }

    @Test
    @DisplayName("写入落盘，重新打开仍可读到")
    void writePersistsToDisk() {
        store.writeSection("providers", List.of(Map.of(
                "id", "p1", "name", "P1", "base_url", "http://x/v1")));

        assertThat(Files.exists(store.path())).isTrue();

        SettingsStore reopened = new SettingsStore(tempDir);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> providers = (List<Map<String, Object>>) reopened.read()
                .get("providers");
        assertThat(providers).hasSize(1);
        assertThat(providers.get(0)).containsEntry("id", "p1");
    }

    @Test
    @DisplayName("未知分组抛 UnknownSection")
    void unknownSectionThrows() {
        assertThatThrownBy(() -> store.section("nope"))
                .isInstanceOf(SettingsStore.UnknownSection.class);
        assertThatThrownBy(() -> store.writeSection("nope", Map.of()))
                .isInstanceOf(SettingsStore.UnknownSection.class);
    }

    @Test
    @DisplayName("旧式单条模型折叠成列表")
    void legacySingleModelFoldsIntoList() {
        store.writeSection("embedding", Map.of("provider", "ollama", "model", "bge-m3"));

        @SuppressWarnings("unchecked")
        Map<String, Object> section = (Map<String, Object>) store.section("embedding");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> models = (List<Map<String, Object>>) section.get("models");
        assertThat(models).hasSize(1);
        assertThat(models.get(0)).containsEntry("id", "m1")
                .containsEntry("provider_id", "ollama")
                .containsEntry("model", "bge-m3");
    }

    @Test
    @DisplayName("未知协议回退 openai；非字典 detection 丢弃")
    void providerNormalisation() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> providers = (List<Map<String, Object>>) store.writeSection(
                "providers", List.of(
                        Map.of("id", "a", "protocol", "banana", "detection", "junk"),
                        Map.of("id", "b", "protocol", "ollama", "detection", Map.of("ok", true))));

        assertThat(providers.get(0)).containsEntry("protocol", "openai")
                .doesNotContainKey("detection");
        assertThat(providers.get(1)).containsEntry("protocol", "ollama")
                .containsKey("detection");
    }

    @Test
    @DisplayName("坏 JSON 文件等价于空文件")
    void corruptFileFallsBackToDefaults() throws Exception {
        Files.writeString(store.path(), "{ not json");

        assertThat(store.read().get("llm"))
                .isEqualTo(Map.of("models", List.of(), "active_id", ""));
    }

    @Test
    @DisplayName("文件内容与默认值浅合并：分组是字典则逐键覆盖")
    void fileMergesIntoDefaults() throws Exception {
        Files.writeString(store.path(), "{\"general\": {\"interface_language\": \"en\"}}");

        @SuppressWarnings("unchecked")
        Map<String, Object> general = (Map<String, Object>) store.read().get("general");
        assertThat(general).containsEntry("interface_language", "en")
                .containsEntry("response_language", "zh");
    }
}
