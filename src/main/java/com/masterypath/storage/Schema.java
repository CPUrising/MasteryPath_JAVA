package com.masterypath.storage;

/**
 * 数据库 DDL（spec 01 §五 / {@code 03} §B）。
 *
 * <p><b>逐字对齐参考实现</b> {@code masterypath/learning/storage.py} 的 {@code _SCHEMA}：
 * 表名、列名、类型、默认值、索引都必须一致，否则 Java 版无法直接打开旧库
 * （这是「数据等价」维度的前提）。修改本文件即为行为变化，必须走增量规范。
 *
 * <p>{@code 0033} 追加 {@code quiz_sets} / {@code quiz_items}：纯增量表，
 * 参考实现的既有表结构保持不变。
 */
final class Schema {

    private Schema() {
    }

    static final String DDL = """
            CREATE TABLE IF NOT EXISTS paths (
                path_id    TEXT PRIMARY KEY,
                version    INTEGER NOT NULL,
                data       TEXT NOT NULL,
                updated_at REAL NOT NULL
            );
            CREATE TABLE IF NOT EXISTS events (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                path_id    TEXT NOT NULL,
                revision   INTEGER NOT NULL,
                event_type TEXT NOT NULL,
                payload    TEXT NOT NULL,
                session_id TEXT NOT NULL DEFAULT '',
                turn_id    TEXT NOT NULL DEFAULT '',
                created_at REAL NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_events_path_revision ON events(path_id, revision);
            CREATE TABLE IF NOT EXISTS interactions (
                interaction_id TEXT PRIMARY KEY,
                path_id        TEXT NOT NULL,
                status         TEXT NOT NULL,
                data           TEXT NOT NULL,
                created_at     REAL NOT NULL,
                updated_at     REAL NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_interactions_path_status ON interactions(path_id, status);
            CREATE TABLE IF NOT EXISTS question_bank (
                entry_id   TEXT PRIMARY KEY,
                path_id    TEXT NOT NULL,
                data       TEXT NOT NULL,
                created_at REAL NOT NULL,
                updated_at REAL NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_bank_path ON question_bank(path_id);
            CREATE TABLE IF NOT EXISTS sessions (
                session_id           TEXT PRIMARY KEY,
                path_id              TEXT NOT NULL,
                title                TEXT NOT NULL DEFAULT '',
                mode                 TEXT NOT NULL DEFAULT 'study',
                status               TEXT NOT NULL DEFAULT 'active',
                message_count        INTEGER NOT NULL DEFAULT 0,
                last_message         TEXT NOT NULL DEFAULT '',
                has_pending_question INTEGER NOT NULL DEFAULT 0,
                preferences          TEXT NOT NULL DEFAULT '{}',
                created_at           REAL NOT NULL,
                updated_at           REAL NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_sessions_path ON sessions(path_id);
            CREATE TABLE IF NOT EXISTS messages (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                turn_id    TEXT NOT NULL DEFAULT '',
                role       TEXT NOT NULL,
                content    TEXT NOT NULL DEFAULT '',
                data       TEXT NOT NULL DEFAULT '{}',
                created_at REAL NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_messages_session ON messages(session_id, id);
            CREATE TABLE IF NOT EXISTS quiz_sets (
                set_id     TEXT PRIMARY KEY,
                path_id    TEXT NOT NULL,
                session_id TEXT NOT NULL DEFAULT '',
                turn_id    TEXT NOT NULL DEFAULT '',
                status     TEXT NOT NULL DEFAULT 'open',
                data       TEXT NOT NULL,
                created_at REAL NOT NULL,
                updated_at REAL NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_quiz_sets_path ON quiz_sets(path_id);
            CREATE TABLE IF NOT EXISTS quiz_items (
                item_id    TEXT PRIMARY KEY,
                set_id     TEXT NOT NULL,
                seq        INTEGER NOT NULL,
                data       TEXT NOT NULL,
                created_at REAL NOT NULL,
                updated_at REAL NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_quiz_items_set ON quiz_items(set_id, seq);
            """;

    static final String UPSERT_INTERACTION = """
            INSERT INTO interactions(interaction_id, path_id, status, data, created_at, updated_at)
            VALUES(?,?,?,?,?,?)
            ON CONFLICT(interaction_id) DO UPDATE SET
                status=excluded.status, data=excluded.data, updated_at=excluded.updated_at
            """;

    static final String UPSERT_BANK = """
            INSERT INTO question_bank(entry_id, path_id, data, created_at, updated_at)
            VALUES(?,?,?,?,?)
            ON CONFLICT(entry_id) DO UPDATE SET
                data=excluded.data, updated_at=excluded.updated_at
            """;

    static final String UPSERT_SESSION = """
            INSERT INTO sessions(session_id, path_id, title, mode, status,
                message_count, last_message, has_pending_question,
                preferences, created_at, updated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(session_id) DO UPDATE SET
                title=excluded.title, mode=excluded.mode, status=excluded.status,
                message_count=excluded.message_count, last_message=excluded.last_message,
                has_pending_question=excluded.has_pending_question,
                updated_at=excluded.updated_at
            """;
}
