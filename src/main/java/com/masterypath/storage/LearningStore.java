package com.masterypath.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.MasteryInteraction;
import com.masterypath.domain.model.QuestionBankEntry;
import com.masterypath.domain.model.QuizItem;
import com.masterypath.domain.model.QuizSet;
import com.masterypath.domain.model.TimeSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * 一个 workspace 的学习数据库（spec 01 §五 / {@code 03} §B）。
 *
 * <p>设计要点（与参考实现 {@code learning/storage.py} 等价）：
 * <ul>
 *   <li>每个 workspace 一个 SQLite <b>文件</b>：跨 workspace 不竞争（{@code INV-52}）。</li>
 *   <li>{@link #mutate} 在一个 {@code BEGIN IMMEDIATE} 事务内 读 → 回调 → CAS 更新；
 *       状态与事件一起提交或一起回滚（{@code INV-51}）。</li>
 *   <li>乐观冲突有界重试（3 次，抖动退避），耗尽抛 {@link CasConflictException}（{@code INV-11b}）；
 *       前置条件失败抛 {@link PreconditionFailedException} 且<b>永不重试</b>（{@code INV-11c}）。</li>
 *   <li>WAL + {@code busy_timeout} 让写入等待而非抛出数据库忙错误（{@code INV-50}）。</li>
 * </ul>
 *
 * <p>并发模型刻意与参考实现一致：<b>单连接 + 进程内互斥</b>。换成连接池会改变
 * 「同 path 并发写必有一个 CAS 冲突」的语义（{@code TC-B13}）。
 */
public class LearningStore implements AutoCloseable {

    private static final int DEFAULT_RETRIES = 3;
    private static final int BUSY_TIMEOUT_MS = 5000;

    private final Path baseDir;
    private final Path dbPath;
    private final int retries;
    private final ObjectMapper mapper = JsonSupport.objectMapper();
    private final Connection connection;
    private final Object lock = new Object();

    public LearningStore(Path baseDir) {
        this(baseDir, DEFAULT_RETRIES);
    }

    public LearningStore(Path baseDir, int retries) {
        this.baseDir = baseDir;
        this.dbPath = baseDir.resolve("learning.db");
        this.retries = Math.max(1, retries);
        try {
            Files.createDirectories(baseDir);
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
            // PRAGMA journal_mode=WAL 不能在事务内执行，因此初始化阶段保持自动提交，
            // 建表与迁移完成后再切到手动事务模式（参考实现同样是先 PRAGMA 再使用事务）。
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA busy_timeout=" + BUSY_TIMEOUT_MS);
            }
            try (Statement st = connection.createStatement()) {
                for (String sql : Schema.DDL.split(";")) {
                    if (!sql.isBlank()) {
                        st.execute(sql);
                    }
                }
            }
            migrate();
            // 空闲态用自动提交；写事务期间临时切到手动模式，见 beginImmediate()。
            connection.setAutoCommit(true);
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("cannot open learning store at " + baseDir, e);
        }
    }

    /** 幂等加列：兼容更早版本建出的库（参考实现同款）。 */
    private void migrate() throws SQLException {
        boolean hasPreferences = false;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(sessions)")) {
            while (rs.next()) {
                if ("preferences".equals(rs.getString("name"))) {
                    hasPreferences = true;
                }
            }
        }
        if (!hasPreferences) {
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE sessions ADD COLUMN preferences TEXT NOT NULL DEFAULT '{}'");
            }
        }
    }

    public Path dbPath() {
        return dbPath;
    }

    /** 当前 journal 模式（用于断言 WAL 已启用，{@code TC-B14}）。 */
    String journalMode() {
        synchronized (lock) {
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("PRAGMA journal_mode")) {
                return rs.next() ? rs.getString(1) : "";
            } catch (SQLException e) {
                throw new IllegalStateException("cannot read journal mode", e);
            }
        }
    }

    /** 某张表的列名（按定义顺序）；用于验证 DDL 与参考实现逐字一致。 */
    List<String> tableColumns(String table) {
        synchronized (lock) {
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
                List<String> columns = new ArrayList<>();
                while (rs.next()) {
                    columns.add(rs.getString("name"));
                }
                return columns;
            } catch (SQLException e) {
                throw new IllegalStateException("cannot read columns of " + table, e);
            }
        }
    }

    // -- 读 -------------------------------------------------------------------

    /** 读取路径聚合；不存在返回 {@code null} 且<b>不创建</b>记录（{@code TC-B1}）。 */
    public LearningProgress load(String pathId) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT data FROM paths WHERE path_id=?")) {
                ps.setString(1, pathId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? mapper.readValue(rs.getString("data"), LearningProgress.class) : null;
                }
            } catch (SQLException | IOException e) {
                throw new IllegalStateException("load failed: " + pathId, e);
            }
        }
    }

    public List<LearningProgress> listPaths() {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT data FROM paths ORDER BY updated_at DESC");
                 ResultSet rs = ps.executeQuery()) {
                List<LearningProgress> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapper.readValue(rs.getString("data"), LearningProgress.class));
                }
                return result;
            } catch (SQLException | IOException e) {
                throw new IllegalStateException("list paths failed", e);
            }
        }
    }

    /** 返回 {@code revision} 之后的全部事件，按 {@code id} 升序（{@code INV-38}）。 */
    public List<Map<String, Object>> eventsSince(String pathId, int revision) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM events WHERE path_id=? AND revision>? ORDER BY id")) {
                ps.setString(1, pathId);
                ps.setInt(2, revision);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String, Object>> events = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getLong("id"));
                        row.put("path_id", rs.getString("path_id"));
                        row.put("revision", rs.getInt("revision"));
                        row.put("event_type", rs.getString("event_type"));
                        row.put("payload", rs.getString("payload"));
                        row.put("session_id", rs.getString("session_id"));
                        row.put("turn_id", rs.getString("turn_id"));
                        row.put("created_at", rs.getDouble("created_at"));
                        events.add(row);
                    }
                    return events;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("events query failed", e);
            }
        }
    }

    // -- 写 -------------------------------------------------------------------

    /** 插入一个全新路径；已存在则抛 {@link PreconditionFailedException}。 */
    public LearningProgress create(LearningProgress progress) {
        synchronized (lock) {
            try {
                beginImmediate();
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT 1 FROM paths WHERE path_id=?")) {
                    ps.setString(1, progress.getPathId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            throw new PreconditionFailedException("path already exists: " + progress.getPathId());
                        }
                    }
                }
                progress.setVersion(Math.max(1, progress.getVersion()));
                progress.setUpdatedAt(TimeSupport.nowSeconds());
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO paths(path_id, version, data, updated_at) VALUES(?,?,?,?)")) {
                    ps.setString(1, progress.getPathId());
                    ps.setInt(2, progress.getVersion());
                    ps.setString(3, mapper.writeValueAsString(progress));
                    ps.setDouble(4, progress.getUpdatedAt());
                    ps.executeUpdate();
                }
                endTransaction(true);
                return progress;
            } catch (RuntimeException | SQLException | IOException e) {
                rollbackQuietly();
                throw wrap(e);
            }
        }
    }

    /** 无版本校验的便捷写入。 */
    public <T> T mutate(String pathId, Function<Transaction, T> fn) {
        return mutate(pathId, fn, null, false);
    }

    /**
     * 以 CAS 原子地应用 {@code fn}。
     *
     * <p>{@code fn} 除改 {@code tx.progress()} 与调用 {@code tx.emit(...)} 外不得有副作用
     * （{@code INV-11a}）；事件仅在成功时落库。
     *
     * @param expectedVersion 非空时要求当前版本等于它，否则抛前置条件异常
     * @param createIfMissing 为真且路径不存在时以空聚合创建
     */
    public <T> T mutate(String pathId,
                        Function<Transaction, T> fn,
                        Integer expectedVersion,
                        boolean createIfMissing) {
        return retryCas(() -> attemptOnce(pathId, fn, expectedVersion, createIfMissing));
    }

    /**
     * CAS 重试策略：最多 {@code retries} 次，抖动退避，耗尽后抛最后一次冲突。
     *
     * <p>包级可见是为了让「重试次数与耗尽行为」可被直接测试（{@code TC-B11}），
     * 而不必用 mock 替换内部方法。{@link PreconditionFailedException} 不在此捕获，
     * 因此天然不会被重试（{@code INV-11c}）。
     */
    <T> T retryCas(java.util.function.Supplier<T> attempt) {
        CasConflictException last = null;
        for (int attemptIndex = 0; attemptIndex < retries; attemptIndex++) {
            try {
                return attempt.get();
            } catch (CasConflictException e) {
                last = e;
                sleepWithJitter(attemptIndex);
            }
        }
        throw last == null ? new CasConflictException("cas conflict") : last;
    }

    private <T> T attemptOnce(String pathId,
                              Function<Transaction, T> fn,
                              Integer expectedVersion,
                              boolean createIfMissing) {
        synchronized (lock) {
            try {
                beginImmediate();
                Integer oldVersion = null;
                LearningProgress progress;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT version, data FROM paths WHERE path_id=?")) {
                    ps.setString(1, pathId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            oldVersion = rs.getInt("version");
                            if (expectedVersion != null && !expectedVersion.equals(oldVersion)) {
                                throw new PreconditionFailedException(
                                        "version mismatch: expected " + expectedVersion + ", found " + oldVersion);
                            }
                            progress = mapper.readValue(rs.getString("data"), LearningProgress.class);
                        } else {
                            if (!createIfMissing) {
                                throw new PreconditionFailedException("no such path: " + pathId);
                            }
                            progress = new LearningProgress(pathId);
                        }
                    }
                }

                Transaction tx = new Transaction(progress, connection, pathId, mapper);
                T result = fn.apply(tx);

                int newVersion = (oldVersion == null ? 0 : oldVersion) + 1;
                progress.setVersion(newVersion);
                progress.setUpdatedAt(TimeSupport.nowSeconds());
                String json = mapper.writeValueAsString(progress);

                if (oldVersion == null) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO paths(path_id, version, data, updated_at) VALUES(?,?,?,?)")) {
                        ps.setString(1, pathId);
                        ps.setInt(2, newVersion);
                        ps.setString(3, json);
                        ps.setDouble(4, progress.getUpdatedAt());
                        ps.executeUpdate();
                    }
                } else {
                    int affected;
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE paths SET version=?, data=?, updated_at=? WHERE path_id=? AND version=?")) {
                        ps.setInt(1, newVersion);
                        ps.setString(2, json);
                        ps.setDouble(3, progress.getUpdatedAt());
                        ps.setString(4, pathId);
                        ps.setInt(5, oldVersion);
                        affected = ps.executeUpdate();
                    }
                    if (affected == 0) {
                        throw new CasConflictException("cas conflict");
                    }
                }

                insertEvents(pathId, newVersion, tx.pendingEvents());
                endTransaction(true);
                return result;
            } catch (PreconditionFailedException | CasConflictException e) {
                rollbackQuietly();
                throw e;
            } catch (RuntimeException | SQLException | IOException e) {
                rollbackQuietly();
                throw wrap(e);
            }
        }
    }

    private void insertEvents(String pathId, int revision, List<Transaction.PendingEvent> events)
            throws SQLException, IOException {
        if (events.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO events(path_id, revision, event_type, payload, session_id, turn_id, created_at) "
                        + "VALUES(?,?,?,?,?,?,?)")) {
            for (Transaction.PendingEvent event : events) {
                ps.setString(1, pathId);
                ps.setInt(2, revision);
                ps.setString(3, event.eventType());
                ps.setString(4, mapper.writeValueAsString(event.payload()));
                ps.setString(5, event.sessionId());
                ps.setString(6, event.turnId());
                ps.setDouble(7, TimeSupport.nowSeconds());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // -- 题目与题库（独立事务，服务层若在 mutate 内则用 Transaction 版本） ----------

    public MasteryInteraction saveInteraction(MasteryInteraction interaction) {
        synchronized (lock) {
            try {
                beginImmediate();
                interaction.setUpdatedAt(TimeSupport.nowSeconds());
                try (PreparedStatement ps = connection.prepareStatement(Schema.UPSERT_INTERACTION)) {
                    ps.setString(1, interaction.getInteractionId());
                    ps.setString(2, interaction.getPathId());
                    ps.setString(3, interaction.getStatus().value());
                    ps.setString(4, mapper.writeValueAsString(interaction));
                    ps.setDouble(5, interaction.getCreatedAt());
                    ps.setDouble(6, interaction.getUpdatedAt());
                    ps.executeUpdate();
                }
                endTransaction(true);
                return interaction;
            } catch (RuntimeException | SQLException | IOException e) {
                rollbackQuietly();
                throw wrap(e);
            }
        }
    }

    public MasteryInteraction getInteraction(String pathId, String interactionId) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT data FROM interactions WHERE path_id=? AND interaction_id=?")) {
                ps.setString(1, pathId);
                ps.setString(2, interactionId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? mapper.readValue(rs.getString("data"), MasteryInteraction.class) : null;
                }
            } catch (SQLException | IOException e) {
                throw new IllegalStateException("get interaction failed", e);
            }
        }
    }

    /**
     * 当前未终态题目；单槽位语义（{@code INV-02}）。
     *
     * <p>与 {@link Transaction#getOpenInteraction()} 同语义，但用于事务外读取
     * （工具层需要在不写库的前提下判断「是否已有待答题」）。
     *
     * @return 最近创建的非终态交互；没有则 {@code null}
     */
    public MasteryInteraction getOpenInteraction(String pathId) {
        synchronized (lock) {
            String placeholders = String.join(",",
                    Transaction.ACTIVE_STATUSES.stream().map(status -> "?").toList());
            String sql = "SELECT data FROM interactions WHERE path_id=? AND status IN ("
                    + placeholders + ") ORDER BY created_at DESC LIMIT 1";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, pathId);
                for (int index = 0; index < Transaction.ACTIVE_STATUSES.size(); index++) {
                    ps.setString(index + 2, Transaction.ACTIVE_STATUSES.get(index));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next()
                            ? mapper.readValue(rs.getString("data"), MasteryInteraction.class)
                            : null;
                }
            } catch (SQLException | IOException e) {
                throw new IllegalStateException("get open interaction failed", e);
            }
        }
    }

    public QuestionBankEntry saveBankEntry(QuestionBankEntry entry) {
        synchronized (lock) {
            try {
                beginImmediate();
                entry.setUpdatedAt(TimeSupport.nowSeconds());
                try (PreparedStatement ps = connection.prepareStatement(Schema.UPSERT_BANK)) {
                    ps.setString(1, entry.getEntryId());
                    ps.setString(2, entry.getPathId());
                    ps.setString(3, mapper.writeValueAsString(entry));
                    ps.setDouble(4, entry.getCreatedAt());
                    ps.setDouble(5, entry.getUpdatedAt());
                    ps.executeUpdate();
                }
                endTransaction(true);
                return entry;
            } catch (RuntimeException | SQLException | IOException e) {
                rollbackQuietly();
                throw wrap(e);
            }
        }
    }

    public List<QuestionBankEntry> listBankEntries(String pathId) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT data FROM question_bank WHERE path_id=? ORDER BY created_at DESC")) {
                ps.setString(1, pathId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<QuestionBankEntry> entries = new ArrayList<>();
                    while (rs.next()) {
                        entries.add(mapper.readValue(rs.getString("data"), QuestionBankEntry.class));
                    }
                    return entries;
                }
            } catch (SQLException | IOException e) {
                throw new IllegalStateException("list bank entries failed", e);
            }
        }
    }

    /** 按 entryId 读取单条题库条目；不存在返回 {@code null}。 */
    public QuestionBankEntry getBankEntry(String entryId) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT data FROM question_bank WHERE entry_id=?")) {
                ps.setString(1, entryId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next()
                            ? mapper.readValue(rs.getString("data"), QuestionBankEntry.class)
                            : null;
                }
            } catch (SQLException | IOException e) {
                throw new IllegalStateException("get bank entry failed", e);
            }
        }
    }

    /** 跨全部路径读取题库，按创建时间倒序。 */
    public List<QuestionBankEntry> listAllBankEntries() {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT data FROM question_bank ORDER BY created_at DESC")) {
                try (ResultSet rs = ps.executeQuery()) {
                    List<QuestionBankEntry> entries = new ArrayList<>();
                    while (rs.next()) {
                        entries.add(mapper.readValue(rs.getString("data"), QuestionBankEntry.class));
                    }
                    return entries;
                }
            } catch (SQLException | IOException e) {
                throw new IllegalStateException("list all bank entries failed", e);
            }
        }
    }

    /**
     * 按 id 就地修改一条题库条目。
     *
     * <p>题库不是 path 聚合的一部分（独立表），因此不走 CAS；标签修改这类单行更新直接覆盖即可。
     *
     * @return 修改后的条目；不存在返回 {@code null}
     */
    public QuestionBankEntry mutateBankEntry(String entryId,
                                             java.util.function.UnaryOperator<QuestionBankEntry> fn) {
        synchronized (lock) {
            try {
                beginImmediate();
                QuestionBankEntry entry;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT data FROM question_bank WHERE entry_id=?")) {
                    ps.setString(1, entryId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            endTransaction(false);
                            return null;
                        }
                        entry = mapper.readValue(rs.getString("data"), QuestionBankEntry.class);
                    }
                }
                QuestionBankEntry updated = fn.apply(entry);
                updated.setUpdatedAt(TimeSupport.nowSeconds());
                try (PreparedStatement ps = connection.prepareStatement(Schema.UPSERT_BANK)) {
                    ps.setString(1, updated.getEntryId());
                    ps.setString(2, updated.getPathId());
                    ps.setString(3, mapper.writeValueAsString(updated));
                    ps.setDouble(4, updated.getCreatedAt());
                    ps.setDouble(5, updated.getUpdatedAt());
                    ps.executeUpdate();
                }
                endTransaction(true);
                return updated;
            } catch (RuntimeException | SQLException | IOException e) {
                rollbackQuietly();
                throw wrap(e);
            }
        }
    }

    // -- 会话与消息 -------------------------------------------------------------

    public Map<String, Object> upsertSession(Map<String, Object> session) {
        double now = TimeSupport.nowSeconds();
        session.putIfAbsent("created_at", now);
        session.put("updated_at", now);
        synchronized (lock) {
            try {
                beginImmediate();
                try (PreparedStatement ps = connection.prepareStatement(Schema.UPSERT_SESSION)) {
                    ps.setString(1, String.valueOf(session.get("session_id")));
                    ps.setString(2, String.valueOf(session.get("path_id")));
                    ps.setString(3, str(session.get("title")));
                    ps.setString(4, strOrDefault(session.get("mode"), "study"));
                    ps.setString(5, strOrDefault(session.get("status"), "active"));
                    ps.setInt(6, toInt(session.get("message_count")));
                    ps.setString(7, str(session.get("last_message")));
                    ps.setInt(8, Boolean.TRUE.equals(session.get("has_pending_question")) ? 1 : 0);
                    ps.setString(9, mapper.writeValueAsString(
                            session.get("preferences") == null ? Map.of() : session.get("preferences")));
                    ps.setDouble(10, toDouble(session.get("created_at")));
                    ps.setDouble(11, toDouble(session.get("updated_at")));
                    ps.executeUpdate();
                }
                endTransaction(true);
                return session;
            } catch (RuntimeException | SQLException | IOException e) {
                rollbackQuietly();
                throw wrap(e);
            }
        }
    }

    public Map<String, Object> getSession(String sessionId) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM sessions WHERE session_id=?")) {
                ps.setString(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rowToSession(rs) : null;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("get session failed", e);
            }
        }
    }

    public List<Map<String, Object>> listSessions(String pathId) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM sessions WHERE path_id=? ORDER BY updated_at DESC")) {
                ps.setString(1, pathId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String, Object>> sessions = new ArrayList<>();
                    while (rs.next()) {
                        sessions.add(rowToSession(rs));
                    }
                    return sessions;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("list sessions failed", e);
            }
        }
    }

    /** 合并写入会话偏好，其他字段不动。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> setSessionPreferences(String sessionId, Map<String, Object> preferences) {
        synchronized (lock) {
            try {
                Map<String, Object> existing = getSession(sessionId);
                if (existing == null) {
                    return null;
                }
                Object raw = existing.get("preferences");
                Map<String, Object> merged = raw instanceof Map<?, ?> map
                        ? new LinkedHashMap<>((Map<String, Object>) map)
                        : new LinkedHashMap<>();
                if (preferences != null) {
                    merged.putAll(preferences);
                }
                beginImmediate();
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE sessions SET preferences=?, updated_at=? WHERE session_id=?")) {
                    ps.setString(1, mapper.writeValueAsString(merged));
                    ps.setDouble(2, TimeSupport.nowSeconds());
                    ps.setString(3, sessionId);
                    ps.executeUpdate();
                }
                endTransaction(true);
                return getSession(sessionId);
            } catch (SQLException | IOException e) {
                rollbackQuietly();
                throw new IllegalStateException("set session preferences failed", e);
            }
        }
    }

    /** 把会话移到另一条路径，其他字段不动。 */
    public Map<String, Object> setSessionPath(String sessionId, String pathId) {
        synchronized (lock) {
            try {
                beginImmediate();
                int affected;
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE sessions SET path_id=?, updated_at=? WHERE session_id=?")) {
                    ps.setString(1, pathId);
                    ps.setDouble(2, TimeSupport.nowSeconds());
                    ps.setString(3, sessionId);
                    affected = ps.executeUpdate();
                }
                endTransaction(true);
                return affected == 0 ? null : getSession(sessionId);
            } catch (SQLException e) {
                rollbackQuietly();
                throw new IllegalStateException("set session path failed", e);
            }
        }
    }

    /**
     * 保存题卷与题目（新增或覆盖结果）。
     *
     * <p>{@code quiz_sets} / {@code quiz_items} 是独立聚合，不走 path 的 CAS；
     * 题卷生成时一次写入整套题，提交时再次写入判分结果。
     */
    public void saveQuizSet(QuizSet set, List<QuizItem> items) {
        if (set == null) {
            throw new IllegalArgumentException("quiz set is required");
        }
        synchronized (lock) {
            try {
                beginImmediate();
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO quiz_sets(set_id,path_id,session_id,turn_id,status,data,"
                                + "created_at,updated_at) VALUES(?,?,?,?,?,?,?,?) "
                                + "ON CONFLICT(set_id) DO UPDATE SET "
                                + "status=excluded.status, data=excluded.data, updated_at=excluded.updated_at")) {
                    ps.setString(1, set.getSetId());
                    ps.setString(2, set.getPathId());
                    ps.setString(3, set.getSessionId());
                    ps.setString(4, set.getTurnId());
                    ps.setString(5, set.getStatus());
                    ps.setString(6, mapper.writeValueAsString(set));
                    ps.setDouble(7, set.getCreatedAt());
                    ps.setDouble(8, set.getSubmittedAt() > 0
                            ? set.getSubmittedAt() : TimeSupport.nowSeconds());
                    ps.executeUpdate();
                }
                for (QuizItem item : items == null ? List.<QuizItem>of() : items) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO quiz_items(item_id,set_id,seq,data,created_at,updated_at) "
                                    + "VALUES(?,?,?,?,?,?) ON CONFLICT(item_id) DO UPDATE SET "
                                    + "seq=excluded.seq, data=excluded.data, updated_at=excluded.updated_at")) {
                        ps.setString(1, item.getItemId());
                        ps.setString(2, item.getSetId());
                        ps.setInt(3, item.getSeq());
                        ps.setString(4, mapper.writeValueAsString(item));
                        ps.setDouble(5, item.getUpdatedAt());
                        ps.setDouble(6, item.getUpdatedAt());
                        ps.executeUpdate();
                    }
                }
                endTransaction(true);
            } catch (RuntimeException | SQLException | IOException e) {
                rollbackQuietly();
                throw wrap(e);
            }
        }
    }

    /** 读取题卷元数据；不存在返回 {@code null}。 */
    public QuizSet getQuizSet(String setId) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT data FROM quiz_sets WHERE set_id=?")) {
                ps.setString(1, setId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? mapper.readValue(rs.getString("data"), QuizSet.class) : null;
                }
            } catch (SQLException | IOException e) {
                throw new IllegalStateException("read quiz set failed", e);
            }
        }
    }

    /** 某条路径的题卷列表（新→旧）。 */
    public List<QuizSet> listQuizSets(String pathId) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT data FROM quiz_sets WHERE path_id=? ORDER BY created_at DESC")) {
                ps.setString(1, pathId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<QuizSet> sets = new ArrayList<>();
                    while (rs.next()) {
                        sets.add(mapper.readValue(rs.getString("data"), QuizSet.class));
                    }
                    return sets;
                }
            } catch (SQLException | IOException e) {
                throw new IllegalStateException("list quiz sets failed", e);
            }
        }
    }

    /** 读取题卷的全部题目，按 {@code seq} 升序。 */
    public List<QuizItem> listQuizItems(String setId) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT data FROM quiz_items WHERE set_id=? ORDER BY seq")) {
                ps.setString(1, setId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<QuizItem> items = new ArrayList<>();
                    while (rs.next()) {
                        items.add(mapper.readValue(rs.getString("data"), QuizItem.class));
                    }
                    return items;
                }
            } catch (SQLException | IOException e) {
                throw new IllegalStateException("list quiz items failed", e);
            }
        }
    }

    /**
     * 级联删除一个路径及其从属数据。
     *
     * <p>顺序固定：{@code messages}（按该路径的会话子查询）→ {@code sessions} →
     * {@code quiz_items} → {@code quiz_sets} → {@code question_bank} → {@code interactions}
     * → {@code paths}，全部在同一事务内。
     *
     * <p><b>不删除 {@code events}</b>：事件表是 append-only 审计日志（{@code INV-38}），
     * 删路径后其历史事件仍可通过 {@link #eventsSince} 读到。
     *
     * @return 路径行被删除时为 {@code true}；路径不存在返回 {@code false}（不抛错）
     */
    public boolean deletePath(String pathId) {
        synchronized (lock) {
            try {
                beginImmediate();
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM messages WHERE session_id IN "
                                + "(SELECT session_id FROM sessions WHERE path_id=?)")) {
                    ps.setString(1, pathId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM sessions WHERE path_id=?")) {
                    ps.setString(1, pathId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM quiz_items WHERE set_id IN "
                                + "(SELECT set_id FROM quiz_sets WHERE path_id=?)")) {
                    ps.setString(1, pathId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM quiz_sets WHERE path_id=?")) {
                    ps.setString(1, pathId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM question_bank WHERE path_id=?")) {
                    ps.setString(1, pathId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM interactions WHERE path_id=?")) {
                    ps.setString(1, pathId);
                    ps.executeUpdate();
                }
                int deleted;
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM paths WHERE path_id=?")) {
                    ps.setString(1, pathId);
                    deleted = ps.executeUpdate();
                }
                endTransaction(true);
                return deleted > 0;
            } catch (SQLException e) {
                rollbackQuietly();
                throw new IllegalStateException("delete path failed", e);
            }
        }
    }

    /** 删除会话及其消息；路径本身不受影响。 */
    public Map<String, Object> deleteSession(String sessionId) {
        Map<String, Object> session = getSession(sessionId);
        if (session == null) {
            return null;
        }
        synchronized (lock) {
            try {
                beginImmediate();
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM messages WHERE session_id=?")) {
                    ps.setString(1, sessionId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM sessions WHERE session_id=?")) {
                    ps.setString(1, sessionId);
                    ps.executeUpdate();
                }
                endTransaction(true);
                return session;
            } catch (SQLException e) {
                rollbackQuietly();
                throw new IllegalStateException("delete session failed", e);
            }
        }
    }

    public void appendMessage(String sessionId, String role, String content, String turnId,
                              Map<String, Object> data) {
        synchronized (lock) {
            try {
                beginImmediate();
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO messages(session_id, turn_id, role, content, data, created_at) "
                                + "VALUES(?,?,?,?,?,?)")) {
                    ps.setString(1, sessionId);
                    ps.setString(2, turnId == null ? "" : turnId);
                    ps.setString(3, role);
                    ps.setString(4, content == null ? "" : content);
                    ps.setString(5, mapper.writeValueAsString(data == null ? Map.of() : data));
                    ps.setDouble(6, TimeSupport.nowSeconds());
                    ps.executeUpdate();
                }
                endTransaction(true);
            } catch (SQLException | IOException e) {
                rollbackQuietly();
                throw new IllegalStateException("append message failed", e);
            }
        }
    }

    /**
     * 会话消息列表（参考实现 {@code list_messages}）。
     *
     * <p>形状必须与参考实现一致：6 个键（不含 {@code session_id}），
     * {@code data} 是<b>已解析的对象</b>而不是原始 JSON 字符串；空值给 {@code {}}。
     * 验收（`0024`）用真实旧库对比时发现了这两处偏差。
     */
    public List<Map<String, Object>> listMessages(String sessionId) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM messages WHERE session_id=? ORDER BY id")) {
                ps.setString(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String, Object>> messages = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getLong("id"));
                        row.put("turn_id", rs.getString("turn_id"));
                        row.put("role", rs.getString("role"));
                        row.put("content", rs.getString("content"));
                        row.put("data", parseMessageData(rs.getString("data")));
                        row.put("created_at", rs.getDouble("created_at"));
                        messages.add(row);
                    }
                    return messages;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("list messages failed", e);
            }
        }
    }

    /** 消息 {@code data} 列 → 对象；空值等价于 {@code {}}（参考实现 {@code json.loads(x) if x else {}}）。 */
    private Object parseMessageData(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            Object parsed = mapper.readValue(raw, Object.class);
            return parsed == null ? new LinkedHashMap<String, Object>() : parsed;
        } catch (IOException e) {
            return new LinkedHashMap<String, Object>();
        }
    }

    // -- 内部 -----------------------------------------------------------------

    /**
     * 开启写事务。
     *
     * <p>不能用 SQL 的 {@code BEGIN IMMEDIATE}：sqlite-jdbc 在 {@code autoCommit=false} 时
     * 会在执行第一条语句时自动开启事务，此时再发 {@code BEGIN} 就是嵌套事务，
     * 报 {@code cannot start a transaction within a transaction}。
     * 因此统一用 JDBC 事务控制：空闲态自动提交，事务期间手动提交。
     * SQLite 的写事务本身即排他，语义与 {@code BEGIN IMMEDIATE} 等价。
     */
    private void beginImmediate() throws SQLException {
        connection.setAutoCommit(false);
    }

    /**
     * 结束事务并回到空闲态（自动提交）。
     *
     * <p>注意：本方法<b>不得</b>在失败路径调用 {@link #rollbackQuietly()}，
     * 否则会形成「回滚失败 → 再回滚 → 再失败」的无限递归（曾触发 StackOverflowError）。
     */
    private void endTransaction(boolean commit) throws SQLException {
        try {
            if (commit) {
                connection.commit();
            } else {
                connection.rollback();
            }
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
                // 恢复自动提交失败时保留原始异常更有价值。
            }
        }
    }

    private void rollbackQuietly() {
        try {
            endTransaction(false);
        } catch (SQLException ignored) {
            // 回滚失败无补救手段；保留原始异常更有价值。
            try {
                connection.setAutoCommit(true);
            } catch (SQLException alsoIgnored) {
                // 同上。
            }
        }
    }

    private static RuntimeException wrap(Exception e) {
        if (e instanceof RuntimeException runtime) {
            return runtime;
        }
        return new IllegalStateException("storage operation failed", e);
    }

    private static void sleepWithJitter(int attempt) {
        long millis = attempt + 1L;
        long jitter = ThreadLocalRandom.current().nextLong(2000);
        try {
            Thread.sleep(millis + jitter / 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while retrying", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rowToSession(ResultSet rs) throws SQLException {
        Map<String, Object> preferences = new LinkedHashMap<>();
        try {
            String raw = rs.getString("preferences");
            if (raw != null && !raw.isBlank()) {
                Object parsed = mapper.readValue(raw, Map.class);
                if (parsed instanceof Map<?, ?> map) {
                    preferences = new LinkedHashMap<>((Map<String, Object>) map);
                }
            }
        } catch (IOException ignored) {
            preferences = new LinkedHashMap<>();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("session_id", rs.getString("session_id"));
        row.put("path_id", rs.getString("path_id"));
        row.put("title", rs.getString("title"));
        row.put("mode", rs.getString("mode"));
        row.put("status", rs.getString("status"));
        row.put("message_count", rs.getInt("message_count"));
        row.put("last_message", rs.getString("last_message"));
        row.put("has_pending_question", rs.getInt("has_pending_question") != 0);
        row.put("preferences", preferences);
        row.put("created_at", rs.getDouble("created_at"));
        row.put("updated_at", rs.getDouble("updated_at"));
        return row;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String strOrDefault(Object value, String fallback) {
        return value == null || String.valueOf(value).isEmpty() ? fallback : String.valueOf(value);
    }

    private static int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    @Override
    public void close() {
        synchronized (lock) {
            try {
                connection.close();
            } catch (SQLException e) {
                throw new IllegalStateException("close store failed", e);
            }
        }
    }
}
