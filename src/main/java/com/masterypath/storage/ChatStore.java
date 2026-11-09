package com.masterypath.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;
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
import java.util.UUID;

/**
 * 普通聊天的会话存储（参考实现 {@code chat/store.py}）。
 *
 * <p><b>独立 {@code chat.db}</b>：与 {@code learning.db} 同目录但不共库。表名相同、列不同，
 * 共库会冲突。一个 workspace 仍只有一份 chat 数据。
 *
 * <p>三处与 {@link LearningStore} 的刻意差异：
 * <ul>
 *   <li>无 CAS、无事件、无 {@code version}——聊天消息是追加式，不需要乐观并发。</li>
 *   <li>{@link #getSession} 对未知 id <b>抛</b> {@link UnknownSession}（对齐参考实现的
 *       {@code KeyError}），而不是返回 {@code null}：调用方需要区分「会话没了」与「空会话」。</li>
 *   <li>{@code message_count} / {@code last_message} 只在 {@link #listSessions()} 里补，
 *       {@link #getSession} 不补——照抄参考实现，否则 {@code session_detail} 的键集合会多出两个。</li>
 * </ul>
 */
public class ChatStore implements AutoCloseable {

    private static final int BUSY_TIMEOUT_MS = 5000;

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS sessions (
                session_id  TEXT PRIMARY KEY,
                title       TEXT NOT NULL DEFAULT '',
                model_id    TEXT NOT NULL DEFAULT '',
                kb_ids      TEXT NOT NULL DEFAULT '[]',
                preferences TEXT NOT NULL DEFAULT '{}',
                created_at  REAL NOT NULL,
                updated_at  REAL NOT NULL
            );
            CREATE TABLE IF NOT EXISTS messages (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id  TEXT NOT NULL,
                role        TEXT NOT NULL,
                content     TEXT NOT NULL DEFAULT '',
                attachments TEXT NOT NULL DEFAULT '[]',
                created_at  REAL NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_chat_messages ON messages(session_id, id);
            """;

    private final Path baseDir;
    private final Path dbPath;
    private final ObjectMapper mapper = JsonSupport.objectMapper();
    private final Connection connection;
    private final Object lock = new Object();

    public ChatStore(Path baseDir) {
        this.baseDir = baseDir;
        this.dbPath = baseDir.resolve("chat.db");
        try {
            Files.createDirectories(baseDir);
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA busy_timeout=" + BUSY_TIMEOUT_MS);
            }
            try (Statement st = connection.createStatement()) {
                for (String sql : DDL.split(";")) {
                    if (!sql.isBlank()) {
                        st.execute(sql);
                    }
                }
            }
            migrate();
            connection.setAutoCommit(true);
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("cannot open chat store at " + baseDir, e);
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

    /** 数据库文件路径（测试用于断言独立于 {@code learning.db}）。 */
    public Path dbPath() {
        return dbPath;
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

    // -- 会话 -----------------------------------------------------------------

    /** 全部会话，按 {@code updated_at} 降序，并补 {@code message_count} / {@code last_message}。 */
    public List<Map<String, Object>> listSessions() {
        synchronized (lock) {
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT * FROM sessions ORDER BY updated_at DESC")) {
                List<Map<String, Object>> sessions = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> session = sessionRow(rs);
                    session.put("message_count", messageCount(session.get("session_id")));
                    session.put("last_message", lastMessage(session.get("session_id")));
                    sessions.add(session);
                }
                return sessions;
            } catch (SQLException e) {
                throw new IllegalStateException("list chat sessions failed", e);
            }
        }
    }

    /**
     * 新建会话；id 形如 {@code c} + 12 位 hex（参考实现 {@code f"c{uuid4().hex[:12]}"}）。
     *
     * <p>前缀 {@code c} 让前端能区分 chat 会话与 learning 会话。
     */
    public Map<String, Object> createSession(String title, String modelId,
                                             List<String> kbIds, Map<String, Object> preferences) {
        String sessionId = "c" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        double now = TimeSupport.nowSeconds();
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sessions(session_id,title,model_id,kb_ids,preferences,"
                            + "created_at,updated_at) VALUES(?,?,?,?,?,?,?)")) {
                ps.setString(1, sessionId);
                ps.setString(2, title == null ? "" : title);
                ps.setString(3, modelId == null ? "" : modelId);
                ps.setString(4, writeJson(kbIds == null ? List.of() : kbIds));
                ps.setString(5, writeJson(preferences == null ? Map.of() : preferences));
                ps.setDouble(6, now);
                ps.setDouble(7, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("create chat session failed", e);
            }
        }
        return getSession(sessionId);
    }

    /**
     * 读一个会话（不含 {@code message_count} / {@code last_message}）。
     *
     * @throws UnknownSession 会话不存在
     */
    public Map<String, Object> getSession(String sessionId) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM sessions WHERE session_id=?")) {
                ps.setString(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new UnknownSession(sessionId);
                    }
                    return sessionRow(rs);
                }
            } catch (SQLException e) {
                throw new IllegalStateException("get chat session failed", e);
            }
        }
    }

    /**
     * 部分更新会话。
     *
     * <p>三条归一（照抄参考实现）：
     * <ul>
     *   <li>{@code kb_ids} 非列表时<b>保留原值</b>，不清空。</li>
     *   <li>{@code preferences} 是<b>合并</b>；非字典的传入被忽略。</li>
     *   <li>{@code preferences.llm_selection.model_id} 回写 legacy {@code model_id} 列。</li>
     * </ul>
     *
     * @throws UnknownSession 会话不存在
     */
    public Map<String, Object> updateSession(String sessionId, Map<String, Object> fields) {
        Map<String, Object> session = getSession(sessionId);
        Map<String, Object> safeFields = fields == null ? Map.of() : fields;
        String title = safeFields.containsKey("title")
                ? str(safeFields.get("title")) : str(session.get("title"));
        String modelId = safeFields.containsKey("model_id")
                ? str(safeFields.get("model_id")) : str(session.get("model_id"));

        Object rawKbIds = safeFields.getOrDefault("kb_ids", session.get("kb_ids"));
        List<?> kbIds = rawKbIds instanceof List<?> list
                ? list : asList(session.get("kb_ids"));

        Map<String, Object> preferences = new LinkedHashMap<>(asMap(session.get("preferences")));
        if (safeFields.get("preferences") instanceof Map<?, ?> incoming) {
            preferences.putAll(castMap(incoming));
        }
        // legacy 列跟随新的真实来源（前端仍读 model_id）。
        if (preferences.get("llm_selection") instanceof Map<?, ?> selection
                && truthy(selection.get("model_id"))) {
            modelId = str(selection.get("model_id"));
        }

        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE sessions SET title=?,model_id=?,kb_ids=?,preferences=?,"
                            + "updated_at=? WHERE session_id=?")) {
                ps.setString(1, title);
                ps.setString(2, modelId);
                ps.setString(3, writeJson(kbIds));
                ps.setString(4, writeJson(preferences));
                ps.setDouble(5, TimeSupport.nowSeconds());
                ps.setString(6, sessionId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("update chat session failed", e);
            }
        }
        return getSession(sessionId);
    }

    /**
     * 删除会话及其消息。
     *
     * @throws UnknownSession 会话不存在（按行数判定，先删后判——照抄参考实现）
     */
    public void deleteSession(String sessionId) {
        int deleted;
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM sessions WHERE session_id=?")) {
                ps.setString(1, sessionId);
                deleted = ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("delete chat session failed", e);
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM messages WHERE session_id=?")) {
                ps.setString(1, sessionId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("delete chat messages failed", e);
            }
        }
        if (deleted == 0) {
            throw new UnknownSession(sessionId);
        }
    }

    // -- 消息 -----------------------------------------------------------------

    /** 会话的全部消息，按 id 升序。 */
    public List<Map<String, Object>> listMessages(String sessionId) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM messages WHERE session_id=? ORDER BY id")) {
                ps.setString(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String, Object>> messages = new ArrayList<>();
                    while (rs.next()) {
                        messages.add(messageRow(rs));
                    }
                    return messages;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("list chat messages failed", e);
            }
        }
    }

    /** 追加一条消息并顺带刷新会话的 {@code updated_at}。 */
    public Map<String, Object> addMessage(String sessionId, String role, String content,
                                          List<Map<String, Object>> attachments) {
        double now = TimeSupport.nowSeconds();
        synchronized (lock) {
            long id;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO messages(session_id,role,content,attachments,created_at) "
                            + "VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, sessionId);
                ps.setString(2, role);
                ps.setString(3, content == null ? "" : content);
                ps.setString(4, writeJson(attachments == null ? List.of() : attachments));
                ps.setDouble(5, now);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    id = keys.next() ? keys.getLong(1) : 0L;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("add chat message failed", e);
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE sessions SET updated_at=? WHERE session_id=?")) {
                ps.setDouble(1, now);
                ps.setString(2, sessionId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("touch chat session failed", e);
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM messages WHERE id=?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? messageRow(rs) : new LinkedHashMap<>();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("read back chat message failed", e);
            }
        }
    }

    /**
     * 撤回：删除指定消息及其之后的所有消息。
     *
     * <p>聊天是追加式的，撤回一条用户消息意味着它之后的所有对话都失去上下文，
     * 因此按 {@code id >= messageId} 整体截断。
     *
     * @return 被删除的消息条数
     * @throws UnknownSession 会话不存在
     */
    public int deleteMessagesFrom(String sessionId, long messageId) {
        getSession(sessionId);
        synchronized (lock) {
            int deleted;
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM messages WHERE session_id=? AND id>=?")) {
                ps.setString(1, sessionId);
                ps.setLong(2, messageId);
                deleted = ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("delete chat messages failed", e);
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE sessions SET updated_at=? WHERE session_id=?")) {
                ps.setDouble(1, TimeSupport.nowSeconds());
                ps.setString(2, sessionId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("touch chat session failed", e);
            }
            return deleted;
        }
    }

    // -- 内部 -----------------------------------------------------------------

    private Map<String, Object> sessionRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("session_id", rs.getString("session_id"));
        row.put("title", rs.getString("title"));
        row.put("model_id", rs.getString("model_id"));
        row.put("kb_ids", asList(readJson(rs.getString("kb_ids"))));
        row.put("preferences", asMap(readJson(rs.getString("preferences"))));
        row.put("created_at", rs.getDouble("created_at"));
        row.put("updated_at", rs.getDouble("updated_at"));
        return row;
    }

    private Map<String, Object> messageRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("session_id", rs.getString("session_id"));
        row.put("role", rs.getString("role"));
        row.put("content", rs.getString("content"));
        row.put("attachments", asList(readJson(rs.getString("attachments"))));
        row.put("created_at", rs.getDouble("created_at"));
        return row;
    }

    private int messageCount(Object sessionId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM messages WHERE session_id=?")) {
            ps.setString(1, String.valueOf(sessionId));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private String lastMessage(Object sessionId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT content FROM messages WHERE session_id=? ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, String.valueOf(sessionId));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("content") : "";
            }
        }
    }

    private Object readJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(raw, Object.class);
        } catch (IOException e) {
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (IOException e) {
            return "{}";
        }
    }

    private static List<?> asList(Object raw) {
        return raw instanceof List<?> list ? list : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object raw) {
        return raw instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
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

    @Override
    public void close() {
        synchronized (lock) {
            try {
                connection.close();
            } catch (SQLException e) {
                throw new IllegalStateException("close chat store failed", e);
            }
        }
    }

    /** 未知会话（参考实现 {@code UnknownSession(KeyError)}）。 */
    public static class UnknownSession extends RuntimeException {
        public UnknownSession(String sessionId) {
            super(sessionId);
        }
    }
}
