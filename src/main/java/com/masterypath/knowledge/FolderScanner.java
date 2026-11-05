package com.masterypath.knowledge;

import com.masterypath.domain.model.TopicSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 文件夹扫描为来源（参考实现 {@code knowledge/loader.py}）。
 *
 * <p>三条规则：
 * <ul>
 *   <li>只收 {@link #SUPPORTED_EXTENSIONS}（{@code .txt} / {@code .md} / {@code .markdown}），
 *       扩展名大小写不敏感。</li>
 *   <li>跳过隐藏项（名字以 {@code .} 开头）与 {@link #SKIP_DIRS} 里的目录。</li>
 *   <li>顺序稳定：按相对路径排序，因此同一文件夹每次扫描得到的 id 序列相同。</li>
 * </ul>
 *
 * <p>{@link #readText} 用 UTF-8 解码并<b>替换</b>坏字节（等价
 * {@code decode("utf-8", errors="replace")}）：扫描不能因一个坏文件整体失败。
 */
public final class FolderScanner {

    /** 支持的扩展名。 */
    public static final List<String> SUPPORTED_EXTENSIONS = List.of(".txt", ".md", ".markdown");

    /** 扫描时跳过的目录名。 */
    public static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", ".venv", "venv", "__pycache__", ".next", "dist", "build");

    /** 摘要字符上限（参考实现 {@code excerpt_chars=500}）。 */
    public static final int DEFAULT_EXCERPT_CHARS = 500;

    /** 读取上限（参考实现 {@code read_text(max_bytes=200_000)}）。 */
    public static final int DEFAULT_MAX_BYTES = 200_000;

    private FolderScanner() {
    }

    /**
     * 递归扫描文件夹，每个可读文本文件一个来源。
     *
     * @param root 文件夹
     * @return 来源列表（按相对路径排序）
     * @throws IOException 文件夹不存在或不是目录
     */
    public static List<TopicSource> scanFolder(Path root) throws IOException {
        return scanFolder(root, DEFAULT_EXCERPT_CHARS);
    }

    /**
     * 递归扫描文件夹。
     *
     * @param excerptChars 摘要字符上限
     * @throws IOException 文件夹不存在或不是目录
     */
    public static List<TopicSource> scanFolder(Path root, int excerptChars) throws IOException {
        if (!Files.exists(root)) {
            throw new java.io.FileNotFoundException("source folder not found: " + root);
        }
        if (!Files.isDirectory(root)) {
            throw new java.nio.file.NotDirectoryException("not a folder: " + root);
        }
        List<TopicSource> sources = new ArrayList<>();
        List<Path> files = walk(root).kept();
        int position = 0;
        for (Path file : files) {
            String rel = relative(root, file);
            TopicSource source = new TopicSource();
            source.setId("src:" + rel);
            source.setKind("file");
            source.setSourceId(rel);
            source.setLabel(rel);
            source.setExcerpt(excerpt(readText(file), excerptChars));
            source.setPosition(position++);
            source.setAvailable(true);
            source.setMetadata(new java.util.LinkedHashMap<>(
                    java.util.Map.of("path", file.toAbsolutePath().toString())));
            sources.add(source);
        }
        return sources;
    }

    /**
     * 读取文本文件（UTF-8，坏字节替换为 U+FFFD）。
     *
     * @param path   文件
     * @param maxBytes 读取上限
     */
    public static String readText(Path path, int maxBytes) throws IOException {
        byte[] data = Files.readAllBytes(path);
        int limit = Math.min(data.length, Math.max(0, maxBytes));
        return new String(data, 0, limit, StandardCharsets.UTF_8);
    }

    /** 默认上限的读取。 */
    public static String readText(Path path) throws IOException {
        return readText(path, DEFAULT_MAX_BYTES);
    }

    // -- 内部 -----------------------------------------------------------------

    /**
     * 扫描结果：保留的文件与被忽略规则丢弃的文件数。
     *
     * @param kept    保留的文本文件（按相对路径排序）
     * @param skipped 被忽略规则丢弃的文件数（隐藏名 / 构建目录）
     */
    public record Walk(List<Path> kept, int skipped) {
    }

    /**
     * 遍历文件夹。
     *
     * <p>{@code skipped} 只统计<b>被忽略规则丢弃</b>的文件；扩展名不支持的直接不计数
     * （参考实现同样如此）。
     */
    public static Walk walk(Path root) {
        List<Path> kept = new ArrayList<>();
        int skipped = 0;
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return new Walk(kept, skipped);
        }
        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(candidates::add);
        } catch (IOException e) {
            return new Walk(kept, skipped);
        }
        candidates.sort(Comparator.comparing(path -> relative(root, path)));
        for (Path candidate : candidates) {
            String rel = relative(root, candidate);
            if (ignored(rel)) {
                skipped++;
                continue;
            }
            if (!SUPPORTED_EXTENSIONS.contains(extension(candidate))) {
                continue;
            }
            kept.add(candidate);
        }
        return new Walk(kept, skipped);
    }

    /** 相对路径的 posix 形式（与参考实现的 {@code relative_to(root).as_posix()} 一致）。 */
    public static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    /** 小写扩展名（含点）；无扩展名返回空串。 */
    public static String extension(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase();
    }

    /** 该相对路径是否应被忽略（任一段以 {@code .} 开头或在 {@link #SKIP_DIRS} 里）。 */
    private static boolean ignored(String rel) {
        for (String part : rel.split("/")) {
            if (part.startsWith(".") || SKIP_DIRS.contains(part)) {
                return true;
            }
        }
        return false;
    }

    /** 摘要：空白折叠后截断（参考实现 {@code " ".join(text.split())[:limit]}）。 */
    static String excerpt(String text, int limit) {
        String collapsed = text.strip().replaceAll("\\s+", " ");
        return collapsed.length() <= limit ? collapsed : collapsed.substring(0, limit);
    }
}
