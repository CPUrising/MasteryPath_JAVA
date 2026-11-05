package com.masterypath.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;
import com.masterypath.knowledge.FolderScanner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 知识库元数据与文件（参考实现 {@code knowledge/store.py}）。
 *
 * <p>目录布局（与参考实现逐字一致）：
 * <pre>
 * &lt;base_dir&gt;/knowledge_bases/&lt;kb_id&gt;/
 *   kb.json     元数据
 *   raw/        一次性导入的文件（保留子目录结构）
 *   index/      本实现的分块与向量（{@code chunks.json}）
 * </pre>
 *
 * <p><b>用文件系统而不是 SQLite</b>：参考实现就是如此，改库会让旧数据无法互通。
 *
 * <p>路径安全用两条正则（逐字复刻）：
 * <ul>
 *   <li>{@link #SAFE_ID}：id 必须是字母数字开头、只含字母数字与 {@code _-}，长度 ≤ 64。</li>
 *   <li>{@link #BAD_NAME}：文件名里不允许出现的字符（Windows 保留字符 + 控制字符）。</li>
 * </ul>
 */
public class KnowledgeStore {

    /** 安全 id（拒绝 {@code ../} 等路径穿越）。 */
    static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");

    /** 文件名里不允许的字符。 */
    static final Pattern BAD_NAME = Pattern.compile("[\\\\/:*?\"<>|\\x00-\\x1f]");

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final Path baseDir;
    private final ObjectMapper mapper = JsonSupport.objectMapper();

    public KnowledgeStore(Path baseDir) {
        this.baseDir = baseDir.resolve("knowledge_bases");
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("cannot create knowledge dir at " + this.baseDir, e);
        }
    }

    /** 知识库根目录（测试用）。 */
    public Path baseDir() {
        return baseDir;
    }

    // -- 路径 -----------------------------------------------------------------

    /** 某个知识库的目录；id 不安全或越界时抛 {@link UnknownKnowledgeBase}。 */
    public Path dir(String kbId) {
        if (kbId == null || !SAFE_ID.matcher(kbId).matches()) {
            throw new UnknownKnowledgeBase(kbId);
        }
        Path path = baseDir.resolve(kbId).toAbsolutePath().normalize();
        if (!path.startsWith(baseDir.toAbsolutePath().normalize())) {
            throw new UnknownKnowledgeBase(kbId);
        }
        return path;
    }

    public Path rawDir(String kbId) {
        return dir(kbId).resolve("raw");
    }

    public Path indexDir(String kbId) {
        return dir(kbId).resolve("index");
    }

    // -- 元数据 ---------------------------------------------------------------

    /** 读元数据；不存在或不可读时抛错。 */
    public Map<String, Object> load(String kbId) {
        Path path = dir(kbId).resolve("kb.json");
        if (!Files.exists(path)) {
            throw new UnknownKnowledgeBase(kbId);
        }
        Object parsed;
        try {
            parsed = mapper.readValue(Files.readString(path, StandardCharsets.UTF_8), Map.class);
        } catch (IOException e) {
            throw new KnowledgeError("knowledge base metadata is unreadable: " + kbId);
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new KnowledgeError("knowledge base metadata is invalid: " + kbId);
        }
        return castMap(map);
    }

    /** 写元数据（自动刷新 {@code updated_at} / {@code disk_path}）。 */
    public Map<String, Object> save(String kbId, Map<String, Object> meta) {
        meta.put("updated_at", now());
        meta.put("disk_path", dir(kbId).toString());
        try {
            Files.writeString(dir(kbId).resolve("kb.json"),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(meta),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new KnowledgeError("cannot write knowledge base metadata: " + kbId);
        }
        return meta;
    }

    /** 补全展示字段（文档数、跳过数、ISO 时间、默认值）。 */
    Map<String, Object> present(Map<String, Object> meta) {
        Map<String, Integer> stats = scanStats(meta);
        meta.put("doc_count", stats.get("documents"));
        meta.put("skipped_count", stats.get("skipped"));
        meta.put("chunk_count", intOf(meta.get("chunk_count")));
        meta.putIfAbsent("index_config", new LinkedHashMap<>());
        meta.putIfAbsent("indexed_config", new LinkedHashMap<>());
        meta.putIfAbsent("status", "empty");
        meta.putIfAbsent("linked_folders", new ArrayList<>());
        meta.putIfAbsent("rag_provider", "llamaindex");
        meta.putIfAbsent("search_mode", "vector");
        meta.put("created_at_iso", iso(meta.get("created_at")));
        meta.put("updated_at_iso", iso(meta.get("updated_at")));
        meta.put("last_indexed_at_iso", iso(meta.get("last_indexed_at")));
        return meta;
    }

    /** 统计可读与忽略的文件数（{@code raw/} + 全部链接文件夹）。 */
    public Map<String, Integer> scanStats(Map<String, Object> meta) {
        String kbId = str(meta.get("id"));
        if (!SAFE_ID.matcher(kbId).matches()) {
            return Map.of("documents", 0, "skipped", 0);
        }
        int documents = 0;
        int skipped = 0;
        FolderScanner.Walk raw = FolderScanner.walk(rawDir(kbId));
        documents += raw.kept().size();
        skipped += raw.skipped();
        for (Object item : asList(meta.get("linked_folders"))) {
            if (item instanceof Map<?, ?> folder) {
                FolderScanner.Walk walk = FolderScanner.walk(Path.of(str(folder.get("path"))));
                documents += walk.kept().size();
                skipped += walk.skipped();
            }
        }
        return Map.of("documents", documents, "skipped", skipped);
    }

    /** 全部知识库（按 {@code updated_at} 降序）。 */
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!Files.isDirectory(baseDir)) {
            return rows;
        }
        try (Stream<Path> stream = Files.walk(baseDir, 2)) {
            List<Path> metas = stream
                    .filter(path -> "kb.json".equals(path.getFileName() == null
                            ? "" : path.getFileName().toString()))
                    .toList();
            for (Path meta : metas) {
                String kbId = meta.getParent().getFileName().toString();
                try {
                    rows.add(present(load(kbId)));
                } catch (KnowledgeError ignored) {
                    // 坏库直接跳过（参考实现同样 continue）。
                }
            }
        } catch (IOException e) {
            return rows;
        }
        rows.sort(Comparator.comparingDouble(
                (Map<String, Object> row) -> doubleOf(row.get("updated_at"))).reversed());
        return rows;
    }

    public Map<String, Object> get(String kbId) {
        return present(load(kbId));
    }

    /** 建档：目录 + 空 {@code raw/} / {@code index/} + 元数据。 */
    public Map<String, Object> create(String name, String mode, Map<String, Object> embeddingModel,
                                      String description, Map<String, Object> indexConfig) {
        String display = name == null ? "" : name.strip();
        if (display.isEmpty()) {
            throw new KnowledgeError("A knowledge base name is required.");
        }
        String baseId = slug(display);
        String kbId = baseId;
        int counter = 2;
        while (Files.exists(baseDir.resolve(kbId))) {
            kbId = baseId + "-" + counter;
            counter++;
        }
        try {
            Files.createDirectories(baseDir.resolve(kbId).resolve("raw"));
            Files.createDirectories(baseDir.resolve(kbId).resolve("index"));
        } catch (IOException e) {
            throw new KnowledgeError("cannot create knowledge base: " + kbId);
        }
        double now = now();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", kbId);
        meta.put("name", display);
        meta.put("description", description == null ? "" : description.strip());
        meta.put("mode", "link".equals(mode) ? "link" : "import");
        meta.put("rag_provider", "llamaindex");
        meta.put("search_mode", "vector");
        meta.put("embedding_model", embeddingModel == null ? Map.of() : embeddingModel);
        meta.put("index_config", indexConfig == null ? Map.of() : indexConfig);
        meta.put("indexed_config", Map.of());
        meta.put("status", "empty");
        meta.put("error", "");
        meta.put("created_at", now);
        meta.put("updated_at", now);
        meta.put("last_indexed_at", null);
        meta.put("linked_folders", new ArrayList<>());
        save(kbId, meta);
        return present(meta);
    }

    /** 部分更新（白名单字段）。 */
    public Map<String, Object> update(String kbId, Map<String, Object> fields) {
        Map<String, Object> meta = load(kbId);
        List<String> allowed = List.of("name", "description", "embedding_model", "status",
                "error", "index_config");
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (allowed.contains(entry.getKey())) {
                meta.put(entry.getKey(), entry.getValue());
            }
        }
        if (fields.containsKey("name") && str(meta.get("name")).strip().isEmpty()) {
            throw new KnowledgeError("A knowledge base name is required.");
        }
        return present(save(kbId, meta));
    }

    public void delete(String kbId) {
        Path directory = dir(kbId);
        if (!Files.exists(directory)) {
            throw new UnknownKnowledgeBase(kbId);
        }
        deleteRecursively(directory);
    }

    /**
     * 写索引结果。
     *
     * <p>{@code chunkCount} 显式给出时用它；否则在状态非 {@code ready} 时清零
     * （参考实现同样如此）。
     */
    public Map<String, Object> setIndexResult(String kbId, String status, String error,
                                              Double lastIndexedAt,
                                              Map<String, Object> embeddingModel,
                                              Integer chunkCount,
                                              Map<String, Object> indexedConfig) {
        Map<String, Object> meta = load(kbId);
        meta.put("status", status);
        meta.put("error", error == null ? "" : error);
        if (lastIndexedAt != null && lastIndexedAt != 0) {
            meta.put("last_indexed_at", lastIndexedAt);
        }
        if (embeddingModel != null) {
            meta.put("embedding_model", embeddingModel);
        }
        if (indexedConfig != null) {
            meta.put("indexed_config", indexedConfig);
        }
        if (chunkCount != null) {
            meta.put("chunk_count", chunkCount);
        } else if (!"ready".equals(status)) {
            meta.put("chunk_count", 0);
        }
        return present(save(kbId, meta));
    }

    // -- 文件与文件夹 ---------------------------------------------------------

    /** 文件清单（{@code raw/} + 链接文件夹），{@code linked} 标记来源。 */
    public List<Map<String, Object>> files(String kbId) {
        Map<String, Object> meta = load(kbId);
        Path root = rawDir(kbId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Path path : FolderScanner.walk(root).kept()) {
            rows.add(fileRow(FolderScanner.relative(root, path), path, false, null));
        }
        for (Object item : asList(meta.get("linked_folders"))) {
            if (!(item instanceof Map<?, ?> folder)) {
                continue;
            }
            Path base = Path.of(str(folder.get("path")));
            String prefix = base.getFileName() == null ? "linked" : base.getFileName().toString();
            for (Path path : FolderScanner.walk(base).kept()) {
                rows.add(fileRow(prefix + "/" + FolderScanner.relative(base, path),
                        path, true, folder.get("id")));
            }
        }
        return rows;
    }

    /**
     * 写入上传文件。
     *
     * @param conflict {@code overwrite} / {@code skip} / {@code rename}
     * @return 实际写入数量
     */
    public int writeUploads(String kbId, List<Upload> uploads, String conflict) {
        Path root = rawDir(kbId);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new KnowledgeError("cannot create raw dir: " + kbId);
        }
        int written = 0;
        for (Upload upload : uploads) {
            Path target = safeJoin(root, upload.relativePath());
            if (Files.exists(target) && "skip".equals(conflict)) {
                continue;
            }
            if (Files.exists(target) && "rename".equals(conflict)) {
                String name = target.getFileName().toString();
                int dot = name.lastIndexOf('.');
                String stem = dot < 0 ? name : name.substring(0, dot);
                String suffix = dot < 0 ? "" : name.substring(dot);
                int counter = 1;
                while (Files.exists(target)) {
                    target = target.getParent().resolve(stem + "-" + counter + suffix);
                    counter++;
                }
            }
            try {
                Files.createDirectories(target.getParent());
                Files.write(target, upload.data());
            } catch (IOException e) {
                throw new KnowledgeError("cannot write uploaded file: " + upload.relativePath());
            }
            written++;
        }
        if (written > 0) {
            save(kbId, load(kbId));
        }
        return written;
    }

    /** 删除 {@code raw/} 下的一个文件。 */
    public void removeFile(String kbId, String relPath) {
        Path root = rawDir(kbId).toAbsolutePath().normalize();
        Path target = root.resolve(relPath).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new KnowledgeError("Invalid file path.");
        }
        if (!Files.isRegularFile(target)) {
            throw new KnowledgeError("File not found.");
        }
        try {
            Files.delete(target);
        } catch (IOException e) {
            throw new KnowledgeError("File not found.");
        }
    }

    /** 把一个文件夹的内容复制进 {@code raw/}。 */
    public int importFolder(String kbId, String folder) {
        Path source = Path.of(folder);
        if (!Files.exists(source) || !Files.isDirectory(source)) {
            throw new KnowledgeError("Folder not found: " + folder);
        }
        Path root = rawDir(kbId);
        int copied = 0;
        for (Path path : FolderScanner.walk(source).kept()) {
            Path dest = root.resolve(FolderScanner.relative(source, path));
            try {
                Files.createDirectories(dest.getParent());
                Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new KnowledgeError("cannot import file: " + path.getFileName());
            }
            copied++;
        }
        save(kbId, load(kbId));
        return copied;
    }

    /** 链接一个外部文件夹（不复制文件）。 */
    public Map<String, Object> addLinkedFolder(String kbId, String folder) {
        Path source = Path.of(folder);
        if (!Files.exists(source) || !Files.isDirectory(source)) {
            throw new KnowledgeError("Folder not found: " + folder);
        }
        Map<String, Object> meta = load(kbId);
        List<Object> rows = asMutableList(meta.get("linked_folders"));
        String resolved = source.toAbsolutePath().normalize().toString();
        for (Object item : rows) {
            if (item instanceof Map<?, ?> existing && resolved.equals(str(existing.get("path")))) {
                throw new KnowledgeError("That folder is already linked.");
            }
        }
        double now = now();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", "folder-" + (long) (now * 1000));
        entry.put("path", resolved);
        entry.put("file_count", FolderScanner.walk(source).kept().size());
        entry.put("added_at", now);
        entry.put("added_at_iso", iso(now));
        entry.put("last_sync", null);
        rows.add(entry);
        meta.put("linked_folders", rows);
        save(kbId, meta);
        return entry;
    }

    /** 移除一个链接文件夹。 */
    public void removeLinkedFolder(String kbId, String folderId) {
        Map<String, Object> meta = load(kbId);
        List<Object> kept = new ArrayList<>();
        for (Object item : asList(meta.get("linked_folders"))) {
            if (item instanceof Map<?, ?> folder && folderId.equals(str(folder.get("id")))) {
                continue;
            }
            kept.add(item);
        }
        meta.put("linked_folders", kept);
        save(kbId, meta);
    }

    /**
     * 全部可读文档：{@code (label, text)}，{@code raw/} 在前、链接文件夹在后。
     *
     * <p>读取上限 2 MB（参考实现 {@code max_bytes=2_000_000}）；纯空白文档被丢弃。
     */
    public List<Map.Entry<String, String>> collectDocuments(String kbId) {
        Map<String, Object> meta = load(kbId);
        List<Map.Entry<String, String>> documents = new ArrayList<>();
        Path raw = rawDir(kbId);
        for (Path path : FolderScanner.walk(raw).kept()) {
            documents.add(Map.entry(FolderScanner.relative(raw, path), readOrEmpty(path)));
        }
        for (Object item : asList(meta.get("linked_folders"))) {
            if (!(item instanceof Map<?, ?> folder)) {
                continue;
            }
            Path root = Path.of(str(folder.get("path")));
            if (!Files.exists(root)) {
                continue;
            }
            String prefix = root.getFileName() == null ? "linked" : root.getFileName().toString();
            for (Path path : FolderScanner.walk(root).kept()) {
                documents.add(Map.entry(
                        prefix + "/" + FolderScanner.relative(root, path), readOrEmpty(path)));
            }
        }
        List<Map.Entry<String, String>> kept = new ArrayList<>();
        for (Map.Entry<String, String> entry : documents) {
            if (!entry.getValue().strip().isEmpty()) {
                kept.add(entry);
            }
        }
        return kept;
    }

    // -- 辅助 -----------------------------------------------------------------

    /**
     * 把上传的相对路径解析到 {@code root} 之下，拒绝穿越。
     *
     * @throws KnowledgeError 空路径、绝对路径、盘符、{@code ..}、非支持扩展名
     */
    static Path safeJoin(Path root, String relative) {
        String text = relative == null ? "" : relative.replace('\\', '/').strip();
        if (text.isEmpty() || text.startsWith("/") || text.matches("^[A-Za-z]:.*")) {
            throw new KnowledgeError("Invalid file path.");
        }
        List<String> parts = new ArrayList<>();
        for (String raw : text.split("/")) {
            if (raw.isEmpty() || ".".equals(raw)) {
                continue;
            }
            if ("..".equals(raw)) {
                throw new KnowledgeError("Invalid file path.");
            }
            String cleaned = BAD_NAME.matcher(raw).replaceAll("").strip();
            while (cleaned.startsWith(".")) {
                cleaned = cleaned.substring(1);
            }
            while (cleaned.endsWith(".")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            if (cleaned.isEmpty()) {
                throw new KnowledgeError("Invalid file path.");
            }
            parts.add(cleaned.length() > 128 ? cleaned.substring(0, 128) : cleaned);
        }
        if (parts.isEmpty()) {
            throw new KnowledgeError("Invalid file path.");
        }
        Path rootResolved = root.toAbsolutePath().normalize();
        Path target = rootResolved.resolve(String.join("/", parts)).normalize();
        if (!target.startsWith(rootResolved) || target.equals(rootResolved)) {
            throw new KnowledgeError("Invalid file path.");
        }
        String extension = FolderScanner.extension(target);
        if (!FolderScanner.SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new KnowledgeError("Unsupported file type: "
                    + (extension.isEmpty() ? "unknown" : extension));
        }
        return target;
    }

    /** 名称 → 目录 id（去掉非法字符，空白折叠为 {@code -}，最多 48 字符）。 */
    static String slug(String name) {
        String cleaned = BAD_NAME.matcher(name == null ? "" : name.strip()).replaceAll("").strip();
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        cleaned = cleaned.replaceAll("\\s+", "-");
        return cleaned.isEmpty() ? "kb" : cleaned.substring(0, Math.min(48, cleaned.length()));
    }

    /** 时间戳 → 本地时区 ISO 字符串；空值返回空串。 */
    public static String iso(Object timestamp) {
        double value = doubleOf(timestamp);
        if (value == 0) {
            return "";
        }
        return Instant.ofEpochMilli((long) (value * 1000))
                .atZone(ZoneId.systemDefault())
                .format(ISO);
    }

    private Map<String, Object> fileRow(String path, Path file, boolean linked, Object folderId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("path", path);
        try {
            row.put("size", Files.size(file));
            row.put("modified_at", iso(Files.getLastModifiedTime(file).toMillis() / 1000.0));
        } catch (IOException e) {
            row.put("size", 0);
            row.put("modified_at", "");
        }
        row.put("linked", linked);
        if (linked) {
            row.put("folder_id", folderId);
        }
        return row;
    }

    private static String readOrEmpty(Path path) {
        try {
            return FolderScanner.readText(path, 2_000_000);
        } catch (IOException e) {
            return "";
        }
    }

    private static void deleteRecursively(Path directory) {
        try (Stream<Path> stream = Files.walk(directory)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new KnowledgeError("cannot delete knowledge base: " + directory.getFileName());
        }
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

    private static List<?> asList(Object raw) {
        return raw instanceof List<?> list ? list : List.of();
    }

    private static List<Object> asMutableList(Object raw) {
        return raw instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    /**
     * 一个待写入的上传文件。
     *
     * @param relativePath 相对路径（会被 {@link #safeJoin} 校验）
     * @param data         内容
     */
    public record Upload(String relativePath, byte[] data) {
    }

    /** 知识库错误（可安全展示给用户）。 */
    public static class KnowledgeError extends RuntimeException {
        public KnowledgeError(String message) {
            super(message);
        }
    }

    /** 未知知识库（映射 404）。 */
    public static class UnknownKnowledgeBase extends KnowledgeError {
        public UnknownKnowledgeBase(String kbId) {
            super(String.valueOf(kbId));
        }
    }
}
