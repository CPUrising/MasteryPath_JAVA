package com.masterypath.service;

import com.masterypath.knowledge.HttpEmbedder;
import com.masterypath.knowledge.VectorIndexer;
import com.masterypath.storage.KnowledgeStore;
import com.masterypath.storage.SettingsStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 知识库服务（参考实现 {@code knowledge/service.py}）。
 *
 * <p>四块职责：
 * <ol>
 *   <li><b>嵌入解析</b>：把设置里的 embedding 分组解析成可调用的 provider 配置。</li>
 *   <li><b>分块配置</b>：全局默认 + 单库覆盖，取值必须落在白名单内。</li>
 *   <li><b>索引</b>：后台线程跑 {@link VectorIndexer#buildIndex}，进度存内存表；
 *       同一库已在索引时重复请求返回<b>同一个</b> {@code run_id}。</li>
 *   <li><b>检索</b>：三重短路（库不存在抛错 / 未就绪返回空 / 检索异常返回空）。</li>
 * </ol>
 *
 * <p>检索路径<b>不调 LLM</b>（{@code INV-32}）。
 */
public class KnowledgeService {

    /** 允许的分块字符数（前端镜像同一份清单，但以后端为准）。 */
    public static final List<Integer> CHUNK_SIZE_CHOICES = List.of(512, 800, 1024, 1536);

    /** 允许的重叠字符数。 */
    public static final List<Integer> CHUNK_OVERLAP_CHOICES = List.of(0, 64, 100, 128, 200);

    /** 默认分块配置。 */
    public static final Map<String, Object> DEFAULT_INDEX_CONFIG =
            Map.of("chunk_size", 512, "chunk_overlap", 64);

    /** 嵌入向量 → provider 配置的工厂（测试注入确定性实现）。 */
    @FunctionalInterface
    public interface EmbedderFactory {
        VectorIndexer.Embedder create(Map<String, Object> config);
    }

    private final KnowledgeStore store;
    private final SettingsStore settings;
    private final EmbedderFactory embedderFactory;
    private final Map<String, Map<String, Object>> progress = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "knowledge-indexer");
        thread.setDaemon(true);
        return thread;
    });

    public KnowledgeService(KnowledgeStore store, SettingsStore settings) {
        this(store, settings, null);
    }

    /**
     * @param embedderFactory {@code null} 时用内置的 HTTP 实现（{@link HttpEmbedder}）
     */
    public KnowledgeService(KnowledgeStore store, SettingsStore settings,
                            EmbedderFactory embedderFactory) {
        this.store = store;
        this.settings = settings;
        this.embedderFactory = embedderFactory == null ? HttpEmbedder::new : embedderFactory;
    }

    public KnowledgeStore store() {
        return store;
    }

    // -- 嵌入解析 -------------------------------------------------------------

    /**
     * 把一个已配置的 embedding 模型解析成 provider 配置。
     *
     * @param modelId 指定模型；{@code null} 时按 {@code active_id} → 第一条
     * @throws KnowledgeStore.KnowledgeError 没有可用模型或提供方缺 base URL
     */
    public Map<String, Object> resolveEmbedding(String modelId) {
        Object rawSection = settings.section("embedding");
        Map<String, Object> section = rawSection instanceof Map<?, ?> map
                ? castMap(map) : Map.of();
        List<?> models = section.get("models") instanceof List<?> list ? list : List.of();

        Map<String, Object> chosen = null;
        if (modelId != null && !modelId.isEmpty()) {
            chosen = findModel(models, modelId);
        }
        if (chosen == null) {
            chosen = findModel(models, str(section.get("active_id")));
        }
        if (chosen == null && !models.isEmpty() && models.get(0) instanceof Map<?, ?> first) {
            chosen = castMap(first);
        }
        if (chosen == null || str(chosen.get("model")).strip().isEmpty()) {
            throw new KnowledgeStore.KnowledgeError(
                    "No embedding model is configured. Add one under Settings → Embedding.");
        }
        String providerId = str(chosen.get("provider_id"));
        Object rawProviders = settings.section("providers");
        Map<String, Object> provider = null;
        if (rawProviders instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> candidate && providerId.equals(str(candidate.get("id")))) {
                    provider = castMap(candidate);
                    break;
                }
            }
        }
        if (provider == null || str(provider.get("base_url")).strip().isEmpty()) {
            throw new KnowledgeStore.KnowledgeError(
                    "The embedding model's provider is missing a base URL. "
                            + "Check Settings → Providers.");
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("model_id", str(chosen.get("id")));
        config.put("provider_id", providerId);
        config.put("name", truthy(chosen.get("name"))
                ? str(chosen.get("name")) : str(chosen.get("model")));
        config.put("model", str(chosen.get("model")));
        config.put("dimension", intOf(chosen.get("dimension")));
        config.put("protocol", truthy(provider.get("protocol"))
                ? str(provider.get("protocol")) : "openai");
        config.put("base_url", str(provider.get("base_url")));
        config.put("api_key", provider.get("api_key") == null ? "" : provider.get("api_key"));
        return config;
    }

    // -- 分块配置 -------------------------------------------------------------

    /**
     * 校验并规范化分块配置。
     *
     * @throws KnowledgeStore.KnowledgeError 非字典、非整数、取值不在白名单、重叠 ≥ 尺寸
     */
    public static Map<String, Object> normalizeChunkConfig(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new KnowledgeStore.KnowledgeError("Chunk settings must be an object.");
        }
        int size;
        int overlap;
        try {
            size = Integer.parseInt(String.valueOf(map.get("chunk_size")).strip());
            overlap = Integer.parseInt(String.valueOf(map.get("chunk_overlap")).strip());
        } catch (NumberFormatException e) {
            throw new KnowledgeStore.KnowledgeError(
                    "Chunk size and overlap must be whole numbers.");
        }
        if (!CHUNK_SIZE_CHOICES.contains(size)) {
            throw new KnowledgeStore.KnowledgeError(
                    "chunk_size must be one of " + CHUNK_SIZE_CHOICES + ".");
        }
        if (!CHUNK_OVERLAP_CHOICES.contains(overlap)) {
            throw new KnowledgeStore.KnowledgeError(
                    "chunk_overlap must be one of " + CHUNK_OVERLAP_CHOICES + ".");
        }
        if (overlap < 0 || overlap >= size) {
            throw new KnowledgeStore.KnowledgeError(
                    "chunk_overlap must be smaller than chunk_size.");
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("chunk_size", size);
        config.put("chunk_overlap", overlap);
        return config;
    }

    /** 全局分块配置；设置里的值非法时回退默认值。 */
    public Map<String, Object> globalIndexConfig() {
        try {
            return normalizeChunkConfig(settings.section("indexing"));
        } catch (KnowledgeStore.KnowledgeError e) {
            return new LinkedHashMap<>(DEFAULT_INDEX_CONFIG);
        }
    }

    /** 单库生效配置：覆盖合法则用它，否则用全局。 */
    public Map<String, Object> effectiveConfig(Map<String, Object> meta) {
        Object override = meta.get("index_config");
        if (override instanceof Map<?, ?> map && !map.isEmpty()) {
            try {
                return normalizeChunkConfig(override);
            } catch (KnowledgeStore.KnowledgeError ignored) {
                // 坏覆盖退回全局（参考实现同样 pass）。
            }
        }
        return globalIndexConfig();
    }

    /** 补全展示字段：{@code effective_config} / {@code index_config_source} / {@code needs_reindex}。 */
    private Map<String, Object> decorate(Map<String, Object> meta) {
        Map<String, Object> effective = effectiveConfig(meta);
        Map<String, Object> indexed = meta.get("indexed_config") instanceof Map<?, ?> map
                ? castMap(map) : Map.of();
        meta.put("effective_config", effective);
        boolean hasOverride = meta.get("index_config") instanceof Map<?, ?> override
                && !override.isEmpty();
        meta.put("index_config_source", hasOverride ? "kb" : "global");
        // 无快照的历史库不可比较（参考实现同样视为不需要重建）。
        boolean same = intOf(indexed.get("chunk_size")) == intOf(effective.get("chunk_size"))
                && intOf(indexed.get("chunk_overlap")) == intOf(effective.get("chunk_overlap"));
        meta.put("needs_reindex", !indexed.isEmpty() && !same);
        return meta;
    }

    // -- CRUD -----------------------------------------------------------------

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> meta : store.list()) {
            rows.add(decorate(meta));
        }
        return rows;
    }

    public Map<String, Object> get(String kbId) {
        return decorate(store.get(kbId));
    }

    /**
     * 建档。
     *
     * <p>嵌入不可用时<b>仍然成功</b>，只是状态置 {@code rag_prerequisite_missing}
     * 并把原因写进 {@code error}。
     */
    public Map<String, Object> create(String name, String mode, String embeddingModelId,
                                      String description, Map<String, Object> indexConfig) {
        Map<String, Object> validated = indexConfig == null || indexConfig.isEmpty()
                ? Map.of() : normalizeChunkConfig(indexConfig);
        Map<String, Object> embedding;
        String status;
        String error;
        try {
            Map<String, Object> config = resolveEmbedding(embeddingModelId);
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("model_id", config.get("model_id"));
            model.put("provider_id", config.get("provider_id"));
            model.put("name", config.get("name"));
            model.put("model", config.get("model"));
            model.put("dimension", config.get("dimension"));
            embedding = model;
            status = "empty";
            error = "";
        } catch (KnowledgeStore.KnowledgeError e) {
            embedding = Map.of();
            status = "rag_prerequisite_missing";
            error = e.getMessage();
        }
        Map<String, Object> meta = store.create(name, mode, embedding, description, validated);
        if (!"empty".equals(status)) {
            meta = store.setIndexResult(str(meta.get("id")), status, error, null, null, null, null);
        }
        return decorate(meta);
    }

    public Map<String, Object> update(String kbId, Map<String, Object> fields) {
        Map<String, Object> payload = new LinkedHashMap<>(fields == null ? Map.of() : fields);
        if (payload.get("index_config") instanceof Map<?, ?> config && !config.isEmpty()) {
            payload.put("index_config", normalizeChunkConfig(config));
        }
        return decorate(store.update(kbId, payload));
    }

    public void delete(String kbId) {
        store.delete(kbId);
        progress.remove(kbId);
    }

    public List<Map<String, Object>> files(String kbId) {
        return store.files(kbId);
    }

    public void removeFile(String kbId, String relPath) {
        store.removeFile(kbId, relPath);
    }

    // -- 摄取 -----------------------------------------------------------------

    public Map<String, Object> upload(String kbId, List<KnowledgeStore.Upload> uploads,
                                      String conflict, boolean background) {
        int written = store.writeUploads(kbId, uploads, conflict);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uploaded", written);
        result.putAll(reindex(kbId, background));
        return result;
    }

    public Map<String, Object> importFolder(String kbId, String folder, boolean background) {
        int count = store.importFolder(kbId, folder);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", count);
        result.putAll(reindex(kbId, background));
        return result;
    }

    public Map<String, Object> addLinkedFolder(String kbId, String folder, boolean background) {
        Map<String, Object> entry = store.addLinkedFolder(kbId, folder);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("folder", entry);
        result.putAll(reindex(kbId, background));
        return result;
    }

    public Map<String, Object> removeLinkedFolder(String kbId, String folderId) {
        store.removeLinkedFolder(kbId, folderId);
        if (!store.collectDocuments(kbId).isEmpty()) {
            return reindex(kbId, true);
        }
        return store.setIndexResult(kbId, "empty", "", null, null, null, null);
    }

    // -- 进度 -----------------------------------------------------------------

    /** 当前运行 id（无运行记录时为空串）。 */
    private String currentRun(String kbId) {
        Map<String, Object> entry = progress.get(kbId);
        return entry == null ? "" : str(entry.get("run_id"));
    }

    private void updateProgress(String kbId, String runId, Map<String, Object> fields) {
        Map<String, Object> entry = progress.get(kbId);
        if (entry == null || !runId.equals(str(entry.get("run_id")))) {
            return;
        }
        entry.putAll(fields);
        entry.put("updated_at", now());
    }

    /**
     * 索引进度。
     *
     * @throws KnowledgeStore.UnknownKnowledgeBase 库不存在（404）
     */
    public Map<String, Object> progress(String kbId) {
        store.get(kbId);
        Map<String, Object> entry = progress.get(kbId);
        if (entry != null) {
            Map<String, Object> snapshot = new LinkedHashMap<>(entry);
            snapshot.put("percent", percent(snapshot));
            return snapshot;
        }
        Map<String, Object> meta = store.get(kbId);
        String status = str(meta.getOrDefault("status", "empty"));
        Map<String, Object> idle = new LinkedHashMap<>();
        idle.put("run_id", "");
        idle.put("status", status);
        idle.put("phase", "idle");
        idle.put("indexed_files", 0);
        idle.put("total_files", intOf(meta.get("doc_count")));
        idle.put("chunks", intOf(meta.get("chunk_count")));
        idle.put("error", str(meta.get("error")));
        idle.put("started_at", 0);
        idle.put("updated_at", doubleOf(meta.get("updated_at")));
        idle.put("percent", "ready".equals(status) ? 1.0 : null);
        return idle;
    }

    /** {@code indexed / total}，{@code total <= 0} 时为 {@code null}。 */
    private static Double percent(Map<String, Object> entry) {
        int total = intOf(entry.get("total_files"));
        if (total <= 0) {
            return null;
        }
        int indexed = intOf(entry.get("indexed_files"));
        return Math.max(0.0, Math.min(1.0, (double) indexed / total));
    }

    // -- 索引 -----------------------------------------------------------------

    /**
     * 重建索引。
     *
     * <p>同一库已在索引时返回<b>同一个</b>运行快照（不重复启动）。
     * {@code background=true} 时立即返回 {@code {status, total_files, run_id}}。
     */
    public Map<String, Object> reindex(String kbId, boolean background) {
        store.get(kbId);
        Map<String, Object> existing = progress.get(kbId);
        if (existing != null && "indexing".equals(str(existing.get("status")))) {
            Map<String, Object> snapshot = new LinkedHashMap<>(existing);
            snapshot.put("percent", percent(snapshot));
            return snapshot;
        }
        String runId = UUID.randomUUID().toString().replace("-", "");

        Map<String, Object> config;
        try {
            Map<String, Object> meta = store.get(kbId);
            Object embeddingModel = meta.get("embedding_model");
            String modelId = embeddingModel instanceof Map<?, ?> map
                    ? str(map.get("model_id")) : "";
            config = resolveEmbedding(modelId);
        } catch (KnowledgeStore.KnowledgeError e) {
            store.setIndexResult(kbId, "rag_prerequisite_missing", e.getMessage(),
                    null, null, null, null);
            return store.get(kbId);
        }

        List<Map.Entry<String, String>> documents = store.collectDocuments(kbId);
        if (documents.isEmpty()) {
            store.setIndexResult(kbId, "empty", "", null, null, null, null);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("run_id", runId);
            entry.put("status", "empty");
            entry.put("phase", "idle");
            entry.put("indexed_files", 0);
            entry.put("total_files", 0);
            entry.put("chunks", 0);
            entry.put("error", "");
            entry.put("started_at", now());
            entry.put("updated_at", now());
            progress.put(kbId, entry);
            if (!background) {
                return store.get(kbId);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "empty");
            result.put("total_files", 0);
            result.put("run_id", runId);
            return result;
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("run_id", runId);
        entry.put("status", "indexing");
        entry.put("phase", "scanning");
        entry.put("indexed_files", 0);
        entry.put("total_files", documents.size());
        entry.put("chunks", 0);
        entry.put("error", "");
        entry.put("started_at", now());
        entry.put("updated_at", now());
        progress.put(kbId, entry);
        store.setIndexResult(kbId, "indexing", "", null, null, null, null);
        Map<String, Object> chunk = effectiveConfig(store.get(kbId));

        if (background) {
            executor.submit(() -> runIndex(kbId, config, runId, documents.size(), chunk));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "indexing");
            result.put("total_files", documents.size());
            result.put("run_id", runId);
            return result;
        }
        runIndex(kbId, config, runId, documents.size(), chunk);
        return store.get(kbId);
    }

    private void runIndex(String kbId, Map<String, Object> config, String runId, int total,
                          Map<String, Object> chunk) {
        Map<String, Object> result;
        try {
            VectorIndexer.Embedder embedder = embedderFactory.create(config);
            VectorIndexer.ProgressListener listener = (indexed, count) -> {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("indexed_files", indexed);
                fields.put("total_files", count);
                fields.put("phase", "embedding");
                updateProgress(kbId, runId, fields);
            };
            result = VectorIndexer.buildIndex(store, kbId, store.collectDocuments(kbId), embedder,
                    intOf(chunk.get("chunk_size")), intOf(chunk.get("chunk_overlap")), listener);
        } catch (RuntimeException e) {
            if (runId.equals(currentRun(kbId))) {
                store.setIndexResult(kbId, "error", e.getMessage(), null, null, null, null);
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("status", "error");
                fields.put("error", e.getMessage());
                fields.put("phase", "idle");
                updateProgress(kbId, runId, fields);
            }
            return;
        }
        if (!runId.equals(currentRun(kbId))) {
            // 有更新的运行取代了本次，丢弃过期结果。
            return;
        }
        Map<String, Object> embedding = new LinkedHashMap<>();
        embedding.put("model_id", config.get("model_id"));
        embedding.put("provider_id", config.get("provider_id"));
        embedding.put("name", config.get("name"));
        embedding.put("model", config.get("model"));
        int dimension = intOf(result.get("dimension"));
        embedding.put("dimension", dimension != 0 ? dimension : intOf(config.get("dimension")));

        Map<String, Object> indexedConfig = new LinkedHashMap<>();
        indexedConfig.put("chunk_size", intOf(chunk.get("chunk_size")));
        indexedConfig.put("chunk_overlap", intOf(chunk.get("chunk_overlap")));
        indexedConfig.put("tokenizer", VectorIndexer.TOKENIZER_TAG);

        store.setIndexResult(kbId, "ready", "", now(), embedding,
                intOf(result.get("chunks")), indexedConfig);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("status", "ready");
        fields.put("phase", "persisting");
        fields.put("indexed_files", total);
        fields.put("total_files", total);
        fields.put("chunks", intOf(result.get("chunks")));
        updateProgress(kbId, runId, fields);
    }

    // -- 检索 -----------------------------------------------------------------

    /**
     * 检索。
     *
     * <p>三重短路：库不存在抛 {@link KnowledgeStore.UnknownKnowledgeBase}；
     * 未就绪返回空；检索异常返回空（不能拖垮聊天）。
     *
     * @return {@code [{text, source, score}]}
     */
    public List<Map<String, Object>> search(String kbId, String query, int topK) {
        Map<String, Object> meta = store.get(kbId);
        if (!"ready".equals(str(meta.get("status")))) {
            return List.of();
        }
        Object embeddingModel = meta.get("embedding_model");
        String modelId = embeddingModel instanceof Map<?, ?> map ? str(map.get("model_id")) : "";
        Map<String, Object> resolved;
        try {
            resolved = resolveEmbedding(modelId);
        } catch (KnowledgeStore.KnowledgeError e) {
            return List.of();
        }
        try {
            return VectorIndexer.search(store, kbId, query,
                    embedderFactory.create(resolved), topK);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /**
     * 拼装「Reference material」块（参考实现 {@code knowledge/context.py}）。
     *
     * <p>检索是 best-effort：不可用或不可达的知识库被跳过，而不是让回合失败。
     *
     * @param kbIds 知识库 id 列表
     * @param query 学习者本回合的消息
     * @return 无命中或没有知识库时返回空串
     */
    public String knowledgeContext(List<String> kbIds, String query) {
        if (kbIds == null || kbIds.isEmpty() || query == null || query.strip().isEmpty()) {
            return "";
        }
        List<String> blocks = new ArrayList<>();
        for (String kbId : kbIds) {
            List<Map<String, Object>> hits;
            try {
                hits = search(kbId, query, 4);
            } catch (RuntimeException e) {
                hits = List.of();
            }
            if (hits.isEmpty()) {
                continue;
            }
            String name;
            try {
                Object raw = store.get(kbId).get("name");
                name = truthy(raw) ? str(raw) : kbId;
            } catch (RuntimeException e) {
                name = kbId;
            }
            List<String> joined = new ArrayList<>();
            for (Map<String, Object> hit : hits) {
                Object source = hit.get("source");
                joined.add("[" + (truthy(source) ? str(source) : "source") + "]\n"
                        + str(hit.get("text")));
            }
            blocks.add("# " + name + "\n" + String.join("\n\n", joined));
        }
        if (blocks.isEmpty()) {
            return "";
        }
        return "Reference material:\n\n" + String.join("\n\n", blocks);
    }

    // -- 辅助 -----------------------------------------------------------------

    private static Map<String, Object> findModel(List<?> models, String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return null;
        }
        for (Object item : models) {
            if (item instanceof Map<?, ?> map && modelId.equals(str(map.get("id")))) {
                return castMap(map);
            }
        }
        return null;
    }

    private static double now() {
        return System.currentTimeMillis() / 1000.0;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static double doubleOf(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return new LinkedHashMap<>((Map<String, Object>) map);
    }
}
