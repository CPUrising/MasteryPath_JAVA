package com.masterypath.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;
import com.masterypath.storage.KnowledgeStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库的向量索引与检索（参考实现 {@code knowledge/indexer.py}）。
 *
 * <p><b>刻意不用 LlamaIndex</b>：JVM 上没有等价库，引入一个向量库会带来新的依赖与
 * 持久化格式，反而破坏「数据等价」。本类只保证可观察的检索契约：
 * 分块 → 嵌入 → 按余弦相似度取 top-k → 返回 {@code {text, source, score}}。
 *
 * <p><b>分块按字符窗口 + 重叠</b>，不是 token。参考实现用
 * {@code SentenceSplitter(chunk_size=512, chunk_overlap=64)}（token 单位），
 * 因此同样配置下 chunk 数不同——这是已知的实现偏差，已在 {@code design.md} 决策 2 登记。
 *
 * <p>持久化格式是本实现自己的：{@code index/chunks.json} =
 * {@code [{source, text, embedding}]}。
 *
 * <p><b>检索不调 LLM</b>（{@code INV-32}）：只做向量点积。
 */
public final class VectorIndexer {

    /** 分块文件（本实现的持久化格式）。 */
    public static final String CHUNKS_FILE = "chunks.json";

    /** 嵌入批大小（参考实现 {@code batch_size=32}）。 */
    public static final int BATCH_SIZE = 32;

    /** {@code indexed_config.tokenizer} 的标记；标明与参考实现的差异。 */
    public static final String TOKENIZER_TAG = "char/cl100k_base";

    private static final ObjectMapper MAPPER = JsonSupport.objectMapper();

    private VectorIndexer() {
    }

    /** 一批文本 → 一批向量（参考实现 {@code Embedder}）。 */
    @FunctionalInterface
    public interface Embedder {
        List<List<Double>> embed(List<String> texts);
    }

    /** 索引进度回调：{@code (已索引文件数, 总文件数)}。 */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int indexed, int total);
    }

    /**
     * 建索引并持久化。
     *
     * <p>逐文件处理，因此 {@code onProgress(indexed, total)} 反映的是<b>文件</b>进度；
     * 单个文件内部按 {@link #BATCH_SIZE} 分批嵌入以限制内存与请求大小。
     *
     * @param documents  {@code (label, text)} 列表
     * @param embedder   嵌入函数
     * @param chunkSize  分块字符数
     * @param chunkOverlap 分块重叠字符数
     * @return {@code {documents, chunks, dimension, skipped, chunk_size, chunk_overlap}}
     * @throws IndexUnavailable 没有可读文档
     */
    public static Map<String, Object> buildIndex(KnowledgeStore store, String kbId,
                                                 List<Map.Entry<String, String>> documents,
                                                 Embedder embedder, int chunkSize,
                                                 int chunkOverlap,
                                                 ProgressListener onProgress) {
        if (documents == null || documents.isEmpty()) {
            throw new IndexUnavailable("This knowledge base has no readable documents yet.");
        }
        Path indexDir = store.indexDir(kbId);
        try {
            if (Files.exists(indexDir)) {
                try (var stream = Files.walk(indexDir)) {
                    for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                }
            }
            Files.createDirectories(indexDir);
        } catch (IOException e) {
            throw new IndexUnavailable("cannot prepare index dir: " + kbId);
        }

        int total = documents.size();
        int skipped = 0;
        int dimension = 0;
        List<Map<String, Object>> chunks = new ArrayList<>();
        if (onProgress != null) {
            onProgress.onProgress(0, total);
        }
        for (int index = 1; index <= total; index++) {
            Map.Entry<String, String> document = documents.get(index - 1);
            List<String> pieces = split(document.getValue(), chunkSize, chunkOverlap);
            if (pieces.isEmpty()) {
                // 纯空白文件切不出块（参考实现同样跳过并计数）。
                skipped++;
                if (onProgress != null) {
                    onProgress.onProgress(index, total);
                }
                continue;
            }
            for (int start = 0; start < pieces.size(); start += BATCH_SIZE) {
                List<String> batch = pieces.subList(start, Math.min(pieces.size(), start + BATCH_SIZE));
                List<List<Double>> vectors = embedder.embed(batch);
                if (vectors.size() != batch.size()) {
                    throw new IndexUnavailable(
                            "The embedding provider returned the wrong vector count.");
                }
                for (int offset = 0; offset < batch.size(); offset++) {
                    List<Double> vector = vectors.get(offset);
                    if (dimension == 0 && !vector.isEmpty()) {
                        dimension = vector.size();
                    }
                    Map<String, Object> chunk = new LinkedHashMap<>();
                    chunk.put("source", document.getKey());
                    chunk.put("text", batch.get(offset));
                    chunk.put("embedding", vector);
                    chunks.add(chunk);
                }
            }
            if (onProgress != null) {
                onProgress.onProgress(index, total);
            }
        }
        if (chunks.isEmpty()) {
            throw new IndexUnavailable("This knowledge base has no readable documents yet.");
        }
        try {
            Files.writeString(indexDir.resolve(CHUNKS_FILE),
                    MAPPER.writeValueAsString(chunks), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IndexUnavailable("cannot persist index: " + kbId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documents", total - skipped);
        result.put("chunks", chunks.size());
        result.put("dimension", dimension);
        result.put("skipped", skipped);
        result.put("chunk_size", chunkSize);
        result.put("chunk_overlap", chunkOverlap);
        return result;
    }

    /**
     * 检索：取与查询向量余弦相似度最高的 top-k 块。
     *
     * @return {@code [{text, source, score}]}
     * @throws IndexUnavailable 索引不存在
     */
    public static List<Map<String, Object>> search(KnowledgeStore store, String kbId,
                                                   String query, Embedder embedder, int topK) {
        if (query == null || query.strip().isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> chunks = loadChunks(store, kbId);
        List<Double> queryVector = embedder.embed(List.of(query)).get(0);
        List<Map<String, Object>> scored = new ArrayList<>();
        for (Map<String, Object> chunk : chunks) {
            @SuppressWarnings("unchecked")
            List<Number> embedding = (List<Number>) chunk.get("embedding");
            double score = cosine(queryVector, embedding);
            Map<String, Object> hit = new LinkedHashMap<>();
            hit.put("text", chunk.get("text"));
            hit.put("source", chunk.get("source"));
            hit.put("score", score);
            scored.add(hit);
        }
        scored.sort(Comparator.comparingDouble(
                (Map<String, Object> hit) -> -((Number) hit.get("score")).doubleValue()));
        int limit = Math.max(1, topK);
        return scored.size() <= limit ? scored : new ArrayList<>(scored.subList(0, limit));
    }

    /** 读取已持久化的块；索引不存在时抛错。 */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> loadChunks(KnowledgeStore store, String kbId) {
        Path path = store.indexDir(kbId).resolve(CHUNKS_FILE);
        if (!Files.exists(path)) {
            throw new IndexUnavailable("This knowledge base has not been indexed yet.");
        }
        try {
            Object parsed = MAPPER.readValue(Files.readString(path, StandardCharsets.UTF_8),
                    List.class);
            return parsed instanceof List<?> list
                    ? new ArrayList<>((List<Map<String, Object>>) list) : List.of();
        } catch (IOException e) {
            throw new IndexUnavailable("This knowledge base has not been indexed yet.");
        }
    }

    /**
     * 字符窗口分块。
     *
     * <p>步长 = {@code size - overlap}；最后一块不足 {@code size} 时仍然保留。
     * {@code overlap >= size} 时退化为不重叠（避免死循环）。
     */
    static List<String> split(String text, int chunkSize, int chunkOverlap) {
        List<String> pieces = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return pieces;
        }
        int size = Math.max(1, chunkSize);
        int overlap = Math.max(0, Math.min(chunkOverlap, size - 1));
        int step = Math.max(1, size - overlap);
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(text.length(), start + size);
            String piece = text.substring(start, end);
            if (!piece.isBlank()) {
                pieces.add(piece);
            }
            if (end >= text.length()) {
                break;
            }
        }
        return pieces;
    }

    /** 余弦相似度；长度不等或零向量返回 {@code 0}。 */
    static double cosine(List<Double> a, List<Number> b) {
        if (a == null || b == null || a.size() != b.size()) {
            return 0.0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int index = 0; index < a.size(); index++) {
            double x = a.get(index);
            double y = b.get(index).doubleValue();
            dot += x * y;
            normA += x * x;
            normB += y * y;
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /** 索引不可用（无文档、未建索引、嵌入失败）。 */
    public static class IndexUnavailable extends RuntimeException {
        public IndexUnavailable(String message) {
            super(message);
        }
    }
}
