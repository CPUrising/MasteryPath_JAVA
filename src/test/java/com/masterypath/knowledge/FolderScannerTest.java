package com.masterypath.knowledge;

import com.masterypath.domain.model.TopicSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文件夹扫描为来源（{@code ../MasteryPath/tests/test_knowledge.py} 的 loader 部分）。
 *
 * <p>三条断言：只收支持扩展名、跳过隐藏项与构建目录、顺序稳定。
 */
class FolderScannerTest {

    @TempDir
    Path tempDir;

    private Path folder;

    @BeforeEach
    void setUp() throws IOException {
        folder = tempDir.resolve("materials");
        Files.createDirectories(folder.resolve("sub"));
        Files.writeString(folder.resolve("a.md"), "# Alpha\n\nconcurrency basics");
        Files.writeString(folder.resolve("b.txt"), "Beta notes");
        Files.writeString(folder.resolve("sub/c.md"), "Gamma nested");
        Files.writeString(folder.resolve(".hidden.md"), "hidden");
        Files.write(folder.resolve("d.pdf"), new byte[]{'%', 'P', 'D', 'F'});
        Files.createDirectories(folder.resolve("node_modules"));
        Files.writeString(folder.resolve("node_modules/e.md"), "ignored");
    }

    @Test
    @DisplayName("只收支持的扩展名，跳过隐藏项与构建目录")
    void scanFindsSupportedFilesOnly() throws IOException {
        List<TopicSource> sources = FolderScanner.scanFolder(folder);

        assertThat(sources).extracting(TopicSource::getLabel)
                .containsExactly("a.md", "b.txt", "sub/c.md");
        assertThat(sources).allSatisfy(source -> assertThat(source.getKind()).isEqualTo("file"));
    }

    @Test
    @DisplayName("来源 id 形如 src:<相对路径>，摘要含正文")
    void sourceIdsAndExcerpts() throws IOException {
        List<TopicSource> sources = FolderScanner.scanFolder(folder);

        assertThat(sources).extracting(TopicSource::getId)
                .containsExactly("src:a.md", "src:b.txt", "src:sub/c.md");
        assertThat(sources.get(0).getExcerpt()).contains("Alpha");
        assertThat(sources.get(0).getPosition()).isZero();
        assertThat(sources.get(0).isAvailable()).isTrue();
        assertThat(sources.get(0).getMetadata()).containsKey("path");
    }

    @Test
    @DisplayName("walk 区分保留与被忽略的数量")
    void walkCountsSkipped() {
        FolderScanner.Walk walk = FolderScanner.walk(folder);

        assertThat(walk.kept()).hasSize(3);
        // .hidden.md 与 node_modules/e.md 被忽略规则丢弃；d.pdf 不算跳过。
        assertThat(walk.skipped()).isEqualTo(2);
    }

    @Test
    @DisplayName("坏字节被替换而不是抛错")
    void readTextReplacesBadBytes() throws IOException {
        Path bad = tempDir.resolve("bad.txt");
        Files.write(bad, new byte[]{'o', 'k', ' ', (byte) 0xff, (byte) 0xfe});

        String text = FolderScanner.readText(bad);

        assertThat(text).startsWith("ok ");
        assertThat(text).contains("\uFFFD");
    }

    @Test
    @DisplayName("readText 遵守 maxBytes 上限")
    void readTextHonoursLimit() throws IOException {
        Path file = tempDir.resolve("long.txt");
        Files.writeString(file, "x".repeat(100));

        assertThat(FolderScanner.readText(file, 10)).hasSize(10);
    }

    @Test
    @DisplayName("文件夹不存在 / 不是目录时抛 IOException")
    void missingFolderThrows() {
        assertThatThrownBy(() -> FolderScanner.scanFolder(tempDir.resolve("nope")))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> FolderScanner.scanFolder(folder.resolve("a.md")))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("摘要折叠空白并截断")
    void excerptCollapsesWhitespace() {
        assertThat(FolderScanner.excerpt("  a \n\n b\tc  ", 100)).isEqualTo("a b c");
        assertThat(FolderScanner.excerpt("abcdef", 3)).isEqualTo("abc");
    }

    @Test
    @DisplayName("扩展名大小写不敏感")
    void extensionIsCaseInsensitive() throws IOException {
        Files.writeString(folder.resolve("upper.MD"), "Upper", StandardCharsets.UTF_8);

        assertThat(FolderScanner.scanFolder(folder)).extracting(TopicSource::getLabel)
                .contains("upper.MD");
    }
}
