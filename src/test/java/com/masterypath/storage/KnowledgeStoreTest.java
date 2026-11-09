package com.masterypath.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 知识库元数据与文件（参考实现 {@code knowledge/store.py}）。
 *
 * <p>重点：路径安全（id 白名单 / 上传穿越）、链接文件夹、文档收集。
 */
@Tag("storage")
class KnowledgeStoreTest {

    @TempDir
    Path tempDir;

    private KnowledgeStore store;

    @BeforeEach
    void setUp() {
        store = new KnowledgeStore(tempDir);
    }

    @Test
    @DisplayName("目录布局：knowledge_bases/<id>/{kb.json,raw,index}")
    void directoryLayout() {
        Map<String, Object> meta = store.create("Physics", "import", Map.of(), "", Map.of());
        String kbId = String.valueOf(meta.get("id"));

        assertThat(kbId).isEqualTo("Physics");
        assertThat(store.dir(kbId).resolve("kb.json")).exists();
        assertThat(store.rawDir(kbId)).isDirectory();
        assertThat(store.indexDir(kbId)).isDirectory();
    }

    @Test
    @DisplayName("重名自动加序号")
    void duplicateNameGetsSuffix() {
        store.create("Same", "import", Map.of(), "", Map.of());
        Map<String, Object> second = store.create("Same", "import", Map.of(), "", Map.of());

        assertThat(second).containsEntry("id", "Same-2");
    }

    @Test
    @DisplayName("名称非法字符被清理；空白名字被拒")
    void nameSlugAndValidation() {
        Map<String, Object> meta = store.create("a/b:c*d", "import", Map.of(), "", Map.of());
        assertThat(meta).containsEntry("id", "abcd");

        assertThatThrownBy(() -> store.create("   ", "import", Map.of(), "", Map.of()))
                .isInstanceOf(KnowledgeStore.KnowledgeError.class)
                .hasMessageContaining("name is required");
    }

    @Test
    @DisplayName("不安全的 id 抛 UnknownKnowledgeBase")
    void unsafeIdRejected() {
        assertThatThrownBy(() -> store.get("../etc"))
                .isInstanceOf(KnowledgeStore.UnknownKnowledgeBase.class);
        assertThatThrownBy(() -> store.get("a/b"))
                .isInstanceOf(KnowledgeStore.UnknownKnowledgeBase.class);
        assertThatThrownBy(() -> store.get(""))
                .isInstanceOf(KnowledgeStore.UnknownKnowledgeBase.class);
    }

    @Test
    @DisplayName("未知 id 抛 UnknownKnowledgeBase")
    void unknownIdRejected() {
        assertThatThrownBy(() -> store.get("ghost"))
                .isInstanceOf(KnowledgeStore.UnknownKnowledgeBase.class);
    }

    @Test
    @DisplayName("上传写入 raw/ 并保留子目录")
    void writeUploadsPreservesStructure() {
        String kbId = create();

        int written = store.writeUploads(kbId, List.of(
                new KnowledgeStore.Upload("note.md", "hello".getBytes()),
                new KnowledgeStore.Upload("sub/deep.txt", "deep".getBytes())), "overwrite");

        assertThat(written).isEqualTo(2);
        assertThat(store.rawDir(kbId).resolve("note.md")).exists();
        assertThat(store.rawDir(kbId).resolve("sub/deep.txt")).exists();
    }

    @Test
    @DisplayName("上传路径穿越被拒")
    void uploadTraversalRejected() {
        String kbId = create();

        assertThatThrownBy(() -> store.writeUploads(kbId, List.of(
                new KnowledgeStore.Upload("../evil.md", "x".getBytes())), "overwrite"))
                .isInstanceOf(KnowledgeStore.KnowledgeError.class)
                .hasMessageContaining("Invalid file path");
        assertThatThrownBy(() -> store.writeUploads(kbId, List.of(
                new KnowledgeStore.Upload("/etc/passwd", "x".getBytes())), "overwrite"))
                .isInstanceOf(KnowledgeStore.KnowledgeError.class);
        assertThatThrownBy(() -> store.writeUploads(kbId, List.of(
                new KnowledgeStore.Upload("C:/x.md", "x".getBytes())), "overwrite"))
                .isInstanceOf(KnowledgeStore.KnowledgeError.class);
    }

    @Test
    @DisplayName("不支持的文件类型被拒")
    void unsupportedExtensionRejected() {
        String kbId = create();

        assertThatThrownBy(() -> store.writeUploads(kbId, List.of(
                new KnowledgeStore.Upload("a.pdf", "x".getBytes())), "overwrite"))
                .isInstanceOf(KnowledgeStore.KnowledgeError.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    @DisplayName("conflict=skip 跳过已存在文件")
    void uploadConflictSkip() {
        String kbId = create();
        store.writeUploads(kbId, List.of(
                new KnowledgeStore.Upload("a.md", "first".getBytes())), "overwrite");

        int written = store.writeUploads(kbId, List.of(
                new KnowledgeStore.Upload("a.md", "second".getBytes())), "skip");

        assertThat(written).isZero();
    }

    @Test
    @DisplayName("conflict=rename 生成新文件名")
    void uploadConflictRename() {
        String kbId = create();
        store.writeUploads(kbId, List.of(
                new KnowledgeStore.Upload("a.md", "first".getBytes())), "overwrite");

        store.writeUploads(kbId, List.of(
                new KnowledgeStore.Upload("a.md", "second".getBytes())), "rename");

        assertThat(store.rawDir(kbId).resolve("a-1.md")).exists();
    }

    @Test
    @DisplayName("导入文件夹复制文件并保留结构")
    void importFolderCopies() throws IOException {
        String kbId = create();
        Path source = tempDir.resolve("materials");
        Files.createDirectories(source.resolve("sub"));
        Files.writeString(source.resolve("a.md"), "Alpha");
        Files.writeString(source.resolve("sub/b.md"), "Beta");

        int copied = store.importFolder(kbId, source.toString());

        assertThat(copied).isEqualTo(2);
        assertThat(store.rawDir(kbId).resolve("sub/b.md")).exists();
    }

    @Test
    @DisplayName("链接文件夹不复制文件；重复链接被拒")
    void linkedFolder() throws IOException {
        String kbId = create();
        Path source = tempDir.resolve("linked");
        Files.createDirectories(source);
        Files.writeString(source.resolve("a.md"), "Alpha");

        Map<String, Object> entry = store.addLinkedFolder(kbId, source.toString());

        assertThat(entry).containsEntry("file_count", 1);
        assertThat(store.rawDir(kbId).resolve("a.md")).doesNotExist();
        assertThatThrownBy(() -> store.addLinkedFolder(kbId, source.toString()))
                .isInstanceOf(KnowledgeStore.KnowledgeError.class)
                .hasMessageContaining("already linked");
    }

    @Test
    @DisplayName("files 列出 raw/ 与链接文件夹，linked 标记正确")
    void filesListBothSources() throws IOException {
        String kbId = create();
        store.writeUploads(kbId, List.of(
                new KnowledgeStore.Upload("own.md", "x".getBytes())), "overwrite");
        Path source = tempDir.resolve("ext");
        Files.createDirectories(source);
        Files.writeString(source.resolve("ext.md"), "y");
        store.addLinkedFolder(kbId, source.toString());

        List<Map<String, Object>> rows = store.files(kbId);

        assertThat(rows).extracting(row -> row.get("path"))
                .containsExactly("own.md", "ext/ext.md");
        assertThat(rows.get(0)).containsEntry("linked", false);
        assertThat(rows.get(1)).containsEntry("linked", true);
    }

    @Test
    @DisplayName("collectDocuments 过滤纯空白文档")
    void collectDocumentsSkipsBlank() {
        String kbId = create();
        store.writeUploads(kbId, List.of(
                new KnowledgeStore.Upload("real.md", "content".getBytes()),
                new KnowledgeStore.Upload("blank.md", "   ".getBytes())), "overwrite");

        List<Map.Entry<String, String>> documents = store.collectDocuments(kbId);

        assertThat(documents).extracting(Map.Entry::getKey).containsExactly("real.md");
    }

    @Test
    @DisplayName("scanStats 统计文档数与忽略数")
    void scanStatsCounts() {
        String kbId = create();
        store.writeUploads(kbId, List.of(
                new KnowledgeStore.Upload("a.md", "x".getBytes())), "overwrite");

        Map<String, Object> meta = store.get(kbId);

        assertThat(meta).containsEntry("doc_count", 1).containsEntry("skipped_count", 0);
    }

    @Test
    @DisplayName("删除后 get 抛 UnknownKnowledgeBase")
    void deleteRemovesDirectory() {
        String kbId = create();

        store.delete(kbId);

        assertThatThrownBy(() -> store.get(kbId))
                .isInstanceOf(KnowledgeStore.UnknownKnowledgeBase.class);
    }

    @Test
    @DisplayName("update 只接受白名单字段")
    void updateWhitelist() {
        String kbId = create();

        Map<String, Object> updated = store.update(kbId, Map.of(
                "name", "Renamed", "unknown_field", "x"));

        assertThat(updated).containsEntry("name", "Renamed")
                .doesNotContainKey("unknown_field");
    }

    @Test
    @DisplayName("update 把名字清空时抛错")
    void updateRejectsBlankName() {
        String kbId = create();

        assertThatThrownBy(() -> store.update(kbId, Map.of("name", "  ")))
                .isInstanceOf(KnowledgeStore.KnowledgeError.class)
                .hasMessageContaining("name is required");
    }

    @Test
    @DisplayName("setIndexResult 非 ready 时清零 chunk_count")
    void setIndexResultResetsChunks() {
        String kbId = create();

        Map<String, Object> result = store.setIndexResult(kbId, "error", "boom", null, null, null, null);

        assertThat(result).containsEntry("status", "error")
                .containsEntry("error", "boom")
                .containsEntry("chunk_count", 0);
    }

    @Test
    @DisplayName("list 按 updated_at 降序")
    void listSortedByUpdatedAt() {
        store.create("First", "import", Map.of(), "", Map.of());
        store.create("Second", "import", Map.of(), "", Map.of());

        List<Map<String, Object>> rows = store.list();

        assertThat(rows).hasSize(2);
        double first = ((Number) rows.get(0).get("updated_at")).doubleValue();
        double second = ((Number) rows.get(1).get("updated_at")).doubleValue();
        assertThat(first).isGreaterThanOrEqualTo(second);
    }

    // -- 辅助 -----------------------------------------------------------------

    private String create() {
        return String.valueOf(store.create("KB", "import", Map.of(), "", Map.of()).get("id"));
    }
}
