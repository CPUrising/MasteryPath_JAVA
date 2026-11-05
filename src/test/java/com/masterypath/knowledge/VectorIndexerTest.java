package com.masterypath.knowledge;

import com.masterypath.storage.KnowledgeStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 分块、嵌入与余弦检索（参考实现 {@code knowledge/indexer.py}）。
 *
 * <p>对照 {@code test_build_index_reports_file_progress}：进度从 {@code (0, total)} 到
 * {@code (total, total)}。
 */
class VectorIndexerTest {

    @TempDir
    Path tempDir;

    private KnowledgeStore store;
    private String kbId;

    @BeforeEach
    void setUp() {
        store = new KnowledgeStore(tempDir);
        Map<String, Object> meta = store.create("Progress", "import", Map.of(), "", Map.of());
        kbId = String.valueOf(meta.get("id"));
    }

    @Test
    @DisplayName("按文件报告进度：首帧 (0, total)，末帧 (total, total)")
    void buildReportsFileProgress() throws Exception {
        Path raw = store.rawDir(kbId);
        Files.createDirectories(raw);
        for (int index = 0; index < 3; index++) {
            Files.writeString(raw.resolve("f" + index + ".md"), "word ".repeat(200));
        }
        List<int[]> calls = new ArrayList<>();

        Map<String, Object> result = VectorIndexer.buildIndex(store, kbId,
                store.collectDocuments(kbId), text -> vectors(text), 512, 64,
                (indexed, total) -> calls.add(new int[]{indexed, total}));

        assertThat(calls.get(0)).containsExactly(0, 3);
        assertThat(calls.get(calls.size() - 1)).containsExactly(3, 3);
        assertThat(result).containsEntry("documents", 3);
        assertThat((Integer) result.get("chunks")).isGreaterThan(0);
    }

    @Test
    @DisplayName("更大分块产生更少块")
    void largerChunkYieldsFewerChunks() throws Exception {
        Path raw = store.rawDir(kbId);
        Files.createDirectories(raw);
        Files.writeString(raw.resolve("doc.md"), "内容".repeat(600));

        Map<String, Object> small = VectorIndexer.buildIndex(store, kbId,
                store.collectDocuments(kbId), text -> vectors(text), 512, 64, null);
        Map<String, Object> large = VectorIndexer.buildIndex(store, kbId,
                store.collectDocuments(kbId), text -> vectors(text), 1536, 200, null);

        assertThat((Integer) small.get("chunks")).isGreaterThan((Integer) large.get("chunks"));
    }

    @Test
    @DisplayName("没有可读文档时抛 IndexUnavailable")
    void noDocumentsThrows() {
        assertThatThrownBy(() -> VectorIndexer.buildIndex(store, kbId, List.of(),
                text -> vectors(text), 512, 64, null))
                .isInstanceOf(VectorIndexer.IndexUnavailable.class)
                .hasMessageContaining("no readable documents");
    }

    @Test
    @DisplayName("纯空白文件切不出块，计入 skipped")
    void blankFileIsSkipped() throws Exception {
        Path raw = store.rawDir(kbId);
        Files.createDirectories(raw);
        Files.writeString(raw.resolve("real.md"), "meaningful text here");
        Files.writeString(raw.resolve("blank.md"), "   \n\n\t  ");

        List<Map.Entry<String, String>> documents = new ArrayList<>();
        for (Map.Entry<String, String> entry : store.collectDocuments(kbId)) {
            documents.add(entry);
        }
        // collectDocuments 已过滤空白文档，因此这里手工补一条纯空白来验证 buildIndex 的跳过分支。
        documents.add(Map.entry("blank.md", "   \n"));

        Map<String, Object> result = VectorIndexer.buildIndex(store, kbId, documents,
                text -> vectors(text), 512, 64, null);

        assertThat(result).containsEntry("skipped", 1);
    }

    @Test
    @DisplayName("检索按余弦相似度排序并带来源")
    void searchRanksBySimilarity() throws Exception {
        Path raw = store.rawDir(kbId);
        Files.createDirectories(raw);
        Files.writeString(raw.resolve("gravity.md"), "gravity pulls objects toward a center");
        Files.writeString(raw.resolve("cooking.md"), "boil water add salt and pasta");
        VectorIndexer.buildIndex(store, kbId, store.collectDocuments(kbId),
                text -> vectors(text), 512, 0, null);

        List<Map<String, Object>> hits = VectorIndexer.search(store, kbId, "gravity center",
                text -> vectors(text), 2);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0)).containsEntry("source", "gravity.md");
        assertThat(hits.get(0)).containsKey("text").containsKey("score");
    }

    @Test
    @DisplayName("空查询返回空列表")
    void blankQueryReturnsEmpty() {
        assertThat(VectorIndexer.search(store, kbId, "  ", text -> vectors(text), 5)).isEmpty();
    }

    @Test
    @DisplayName("未建索引时检索抛 IndexUnavailable")
    void searchBeforeIndexThrows() {
        assertThatThrownBy(() -> VectorIndexer.search(store, kbId, "x", text -> vectors(text), 5))
                .isInstanceOf(VectorIndexer.IndexUnavailable.class)
                .hasMessageContaining("not been indexed");
    }

    @Test
    @DisplayName("分块：步长 = size - overlap，末块保留")
    void splitUsesStepAndKeepsTail() {
        List<String> pieces = VectorIndexer.split("abcdefghij", 4, 2);

        assertThat(pieces).containsExactly("abcd", "cdef", "efgh", "ghij");
    }

    @Test
    @DisplayName("余弦：长度不等或零向量为 0")
    void cosineEdgeCases() {
        assertThat(VectorIndexer.cosine(List.of(1.0, 0.0), List.of(1.0))).isZero();
        assertThat(VectorIndexer.cosine(List.of(0.0, 0.0), List.of(1.0, 1.0))).isZero();
        assertThat(VectorIndexer.cosine(List.of(1.0, 0.0), List.of(1.0, 0.0))).isEqualTo(1.0);
    }

    // -- 辅助 -----------------------------------------------------------------

    /** 词袋哈希向量：共享词越多，余弦越高。 */
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
