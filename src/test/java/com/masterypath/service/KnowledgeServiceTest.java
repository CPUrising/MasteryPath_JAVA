package com.masterypath.service;

import com.masterypath.knowledge.VectorIndexer;
import com.masterypath.storage.KnowledgeStore;
import com.masterypath.storage.SettingsStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 知识库服务（{@code ../MasteryPath/tests/test_knowledge_chat.py} 的 KB 部分）。
 *
 * <p>重点：分块配置校验、嵌入缺失时的降级状态、索引进度、检索三重短路、参考块拼装。
 */
class KnowledgeServiceTest {

    @TempDir
    Path tempDir;

    private KnowledgeStore store;
    private SettingsStore settings;
    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        store = new KnowledgeStore(tempDir);
        settings = new SettingsStore(tempDir);
        service = new KnowledgeService(store, settings, config -> texts -> vectors(texts));
    }

    // -- 建档 -----------------------------------------------------------------

    @Test
    @DisplayName("无嵌入模型时建档成功但状态为 rag_prerequisite_missing")
    void createWithoutEmbeddingIsPrerequisiteMissing() {
        Map<String, Object> created = service.create("Empty KB", "import", null, "", null);

        assertThat(created).containsEntry("status", "rag_prerequisite_missing")
                .containsEntry("id", "Empty-KB");
        assertThat(String.valueOf(created.get("error"))).contains("No embedding model");
        assertThat(service.list().get(0)).containsEntry("name", "Empty KB");
    }

    @Test
    @DisplayName("有嵌入模型时状态为 empty，且记录模型信息")
    void createWithEmbeddingIsEmpty() {
        configureEmbedding();

        Map<String, Object> created = service.create("Physics", "import", null, "", null);

        assertThat(created).containsEntry("status", "empty");
        @SuppressWarnings("unchecked")
        Map<String, Object> model = (Map<String, Object>) created.get("embedding_model");
        assertThat(model).containsEntry("model_id", "e1").containsEntry("model", "bge");
    }

    @Test
    @DisplayName("默认分块配置为 512/64，来源标记 global")
    void defaultConfigComesFromGlobal() {
        configureEmbedding();

        Map<String, Object> created = service.create("Chunked", "import", null, "", null);

        assertThat(created).containsEntry("effective_config",
                Map.of("chunk_size", 512, "chunk_overlap", 64));
        assertThat(created).containsEntry("index_config_source", "global");
    }

    @Test
    @DisplayName("单库覆盖分块配置，来源标记 kb")
    void kbOverrideConfig() {
        configureEmbedding();
        Map<String, Object> created = service.create("Override", "import", null, "",
                Map.of("chunk_size", 1024, "chunk_overlap", 128));

        assertThat(created).containsEntry("effective_config",
                Map.of("chunk_size", 1024, "chunk_overlap", 128));
        assertThat(created).containsEntry("index_config_source", "kb");
    }

    @Test
    @DisplayName("全局分块配置变更后生效")
    void globalConfigChangeApplies() {
        configureEmbedding();
        Map<String, Object> created = service.create("Chunked", "import", null, "", null);
        String kbId = String.valueOf(created.get("id"));

        settings.writeSection("indexing", Map.of("chunk_size", 800, "chunk_overlap", 100));

        assertThat(service.get(kbId)).containsEntry("effective_config",
                Map.of("chunk_size", 800, "chunk_overlap", 100));
    }

    @Test
    @DisplayName("非法分块配置被拒")
    void invalidChunkConfigRejected() {
        assertThatThrownBy(() -> service.create("Bad", "import", null, "",
                Map.of("chunk_size", 900, "chunk_overlap", 64)))
                .isInstanceOf(KnowledgeStore.KnowledgeError.class)
                .hasMessageContaining("chunk_size must be one of");
        // overlap 不在白名单时先被白名单拦下（参考实现同样是这条文案）。
        assertThatThrownBy(() -> KnowledgeService.normalizeChunkConfig(
                Map.of("chunk_size", 512, "chunk_overlap", 512)))
                .isInstanceOf(KnowledgeStore.KnowledgeError.class)
                .hasMessageContaining("chunk_overlap must be one of");
        assertThatThrownBy(() -> KnowledgeService.normalizeChunkConfig("not a map"))
                .isInstanceOf(KnowledgeStore.KnowledgeError.class)
                .hasMessageContaining("must be an object");
    }

    // -- 索引 -----------------------------------------------------------------

    @Test
    @DisplayName("索引后状态 ready，chunk_count > 0")
    void indexThenReady() throws IOException {
        configureEmbedding();
        String kbId = createWithDocument("gravity.md", "gravity pulls objects toward a center");

        Map<String, Object> result = service.reindex(kbId, false);

        assertThat(result).containsEntry("status", "ready");
        assertThat((Integer) result.get("chunk_count")).isGreaterThan(0);
        assertThat(result.get("last_indexed_at")).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> indexed = (Map<String, Object>) result.get("indexed_config");
        assertThat(indexed).containsEntry("chunk_size", 512)
                .containsEntry("tokenizer", VectorIndexer.TOKENIZER_TAG);
    }

    @Test
    @DisplayName("无文档时索引状态为 empty，percent 为 null")
    void reindexWithoutDocuments() {
        configureEmbedding();
        String kbId = String.valueOf(service.create("Blank", "import", null, "", null).get("id"));

        service.reindex(kbId, false);

        Map<String, Object> progress = service.progress(kbId);
        assertThat(progress).containsEntry("status", "empty").containsEntry("percent", null);
    }

    @Test
    @DisplayName("进度：ready 时 percent 为 1.0")
    void progressAfterReady() throws IOException {
        configureEmbedding();
        String kbId = createWithDocument("a.md", "some content to embed");
        service.reindex(kbId, false);

        assertThat(service.progress(kbId)).containsEntry("status", "ready")
                .containsEntry("percent", 1.0);
    }

    @Test
    @DisplayName("同一库已在索引时重复请求返回同一 run_id")
    void concurrentReindexReturnsSameRun() throws IOException {
        configureEmbedding();
        String kbId = createWithDocument("slow.md", "content ".repeat(200));

        Map<String, Object> first = service.reindex(kbId, true);
        Map<String, Object> second = service.reindex(kbId, true);

        assertThat(first).containsEntry("status", "indexing");
        assertThat(second.get("run_id")).isEqualTo(first.get("run_id"));
    }

    @Test
    @DisplayName("needs_reindex：改分块配置后为 true")
    void needsReindexAfterConfigChange() throws IOException {
        configureEmbedding();
        String kbId = createWithDocument("a.md", "content ".repeat(50));
        service.reindex(kbId, false);
        assertThat(service.get(kbId)).containsEntry("needs_reindex", false);

        service.update(kbId, Map.of("index_config",
                Map.of("chunk_size", 1024, "chunk_overlap", 128)));

        assertThat(service.get(kbId)).containsEntry("needs_reindex", true);
    }

    @Test
    @DisplayName("未知库的进度请求抛 UnknownKnowledgeBase")
    void progressUnknownThrows() {
        assertThatThrownBy(() -> service.progress("ghost"))
                .isInstanceOf(KnowledgeStore.UnknownKnowledgeBase.class);
    }

    // -- 检索 -----------------------------------------------------------------

    @Test
    @DisplayName("检索命中并按相似度排序")
    void searchReturnsRankedHits() throws IOException {
        configureEmbedding();
        String kbId = createWithDocument("gravity.md", "gravity pulls objects toward a center");
        service.reindex(kbId, false);

        List<Map<String, Object>> hits = service.search(kbId, "gravity center", 3);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0)).containsEntry("source", "gravity.md");
        assertThat(hits.get(0).get("score")).isNotNull();
    }

    @Test
    @DisplayName("未就绪的库检索返回空列表（不抛错）")
    void searchNotReadyReturnsEmpty() {
        configureEmbedding();
        String kbId = String.valueOf(service.create("Blank", "import", null, "", null).get("id"));

        assertThat(service.search(kbId, "anything", 5)).isEmpty();
    }

    @Test
    @DisplayName("未知库检索抛 UnknownKnowledgeBase")
    void searchUnknownThrows() {
        assertThatThrownBy(() -> service.search("ghost", "x", 5))
                .isInstanceOf(KnowledgeStore.UnknownKnowledgeBase.class);
    }

    // -- 参考块 ---------------------------------------------------------------

    @Test
    @DisplayName("knowledgeContext 拼装 Reference material 块")
    void knowledgeContextBuildsBlock() throws IOException {
        configureEmbedding();
        String kbId = createWithDocument("gravity.md", "gravity pulls objects toward a center");
        service.reindex(kbId, false);

        String context = service.knowledgeContext(List.of(kbId), "gravity");

        assertThat(context).startsWith("Reference material:");
        assertThat(context).contains("# KB").contains("gravity.md");
    }

    @Test
    @DisplayName("无知识库或空查询时参考块为空")
    void knowledgeContextEmptyCases() throws IOException {
        configureEmbedding();
        String kbId = createWithDocument("a.md", "content");
        service.reindex(kbId, false);

        assertThat(service.knowledgeContext(List.of(), "q")).isEmpty();
        assertThat(service.knowledgeContext(List.of(kbId), "   ")).isEmpty();
        assertThat(service.knowledgeContext(List.of("ghost"), "q")).isEmpty();
    }

    // -- 辅助 -----------------------------------------------------------------

    private void configureEmbedding() {
        settings.writeSection("providers", List.of(Map.of(
                "id", "ollama",
                "name", "Ollama",
                "base_url", "http://127.0.0.1:11434/v1",
                "api_key", "",
                "protocol", "ollama")));
        settings.writeSection("embedding", Map.of(
                "models", List.of(Map.of(
                        "id", "e1", "provider_id", "ollama",
                        "model", "bge", "name", "bge", "dimension", 8)),
                "active_id", "e1"));
    }

    private String createWithDocument(String name, String content) throws IOException {
        Map<String, Object> created = service.create("KB", "import", null, "", null);
        String kbId = String.valueOf(created.get("id"));
        Path raw = store.rawDir(kbId);
        Files.createDirectories(raw);
        Files.writeString(raw.resolve(name), content);
        return kbId;
    }

    /** 词袋哈希向量（共享词越多余弦越高）。 */
    private static List<List<Double>> vectors(List<String> texts) {
        List<List<Double>> out = new ArrayList<>();
        for (String text : texts) {
            double[] vector = new double[16];
            for (String word : text.toLowerCase().split("\\s+")) {
                if (!word.isEmpty()) {
                    vector[Math.floorMod(word.hashCode(), 16)] += 1.0;
                }
            }
            List<Double> values = new ArrayList<>();
            for (double value : vector) {
                values.add(value);
            }
            out.add(values);
        }
        return out;
    }
}
