package com.masterypath.storage;

import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.MasteryInteraction;
import com.masterypath.domain.model.QuestionBankEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 传给 {@code mutate} 回调的事务句柄（对应参考实现的 {@code Transaction}）。
 *
 * <p>回调只能通过本类改聚合与发事件，因此无法绕过事务边界；题目与题库的读写走<b>同一连接</b>，
 * 使「写 attempt + evidence + interaction」一起提交或一起回滚（{@code INV-51}）。
 */
public class Transaction {

    /** 未终态的题目状态（占据单槽位，{@code INV-02}）。 */
    private static final List<String> ACTIVE_STATUSES = List.of("registered", "awaiting_input", "answered");

    private final LearningProgress progress;
    private final Connection connection;
    private final String pathId;
    private final List<PendingEvent> events = new ArrayList<>();
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    Transaction(LearningProgress progress,
                Connection connection,
                String pathId,
                com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.progress = progress;
        this.connection = connection;
        this.pathId = pathId;
        this.mapper = mapper;
    }

    /** 当前聚合；回调就地修改它。 */
    public LearningProgress progress() {
        return progress;
    }

    /** 排入一条领域事件；只有事务提交成功才会落库（{@code INV-37}）。 */
    public void emit(String eventType, Map<String, Object> payload) {
        emit(eventType, payload, "", "");
    }

    public void emit(String eventType, Map<String, Object> payload, String sessionId, String turnId) {
        events.add(new PendingEvent(eventType,
                payload == null ? new LinkedHashMap<>() : payload,
                sessionId == null ? "" : sessionId,
                turnId == null ? "" : turnId));
    }

    /** 前置条件断言：失败抛 {@link PreconditionFailedException}，永不重试（{@code INV-11c}）。 */
    public void require(boolean condition, String message) {
        if (!condition) {
            throw new PreconditionFailedException(message);
        }
    }

    // -- 题目事务（同一连接） -------------------------------------------------

    public MasteryInteraction getInteraction(String interactionId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT data FROM interactions WHERE path_id=? AND interaction_id=?")) {
            ps.setString(1, pathId);
            ps.setString(2, interactionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs.getString("data"), MasteryInteraction.class) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("read interaction failed", e);
        }
    }

    /** 当前未终态题目；单槽位语义（{@code INV-02}）。 */
    public MasteryInteraction getOpenInteraction() {
        String placeholders = String.join(",", ACTIVE_STATUSES.stream().map(s -> "?").toList());
        String sql = "SELECT data FROM interactions WHERE path_id=? AND status IN (" + placeholders
                + ") ORDER BY created_at DESC LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, pathId);
            for (int i = 0; i < ACTIVE_STATUSES.size(); i++) {
                ps.setString(i + 2, ACTIVE_STATUSES.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs.getString("data"), MasteryInteraction.class) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("read open interaction failed", e);
        }
    }

    public void saveInteraction(MasteryInteraction interaction) {
        interaction.setUpdatedAt(com.masterypath.domain.model.TimeSupport.nowSeconds());
        try (PreparedStatement ps = connection.prepareStatement(Schema.UPSERT_INTERACTION)) {
            ps.setString(1, interaction.getInteractionId());
            ps.setString(2, interaction.getPathId());
            ps.setString(3, interaction.getStatus().value());
            ps.setString(4, write(interaction));
            ps.setDouble(5, interaction.getCreatedAt());
            ps.setDouble(6, interaction.getUpdatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("save interaction failed", e);
        }
    }

    public void saveBankEntry(QuestionBankEntry entry) {
        entry.setUpdatedAt(com.masterypath.domain.model.TimeSupport.nowSeconds());
        try (PreparedStatement ps = connection.prepareStatement(Schema.UPSERT_BANK)) {
            ps.setString(1, entry.getEntryId());
            ps.setString(2, entry.getPathId());
            ps.setString(3, write(entry));
            ps.setDouble(4, entry.getCreatedAt());
            ps.setDouble(5, entry.getUpdatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("save bank entry failed", e);
        }
    }

    /** 同一题目的全部题库条目（修复题目时可能一次更新多条）。 */
    public List<QuestionBankEntry> findBankEntriesByQuestion(String questionId) {
        String wanted = questionId == null ? "" : questionId.trim();
        if (wanted.isEmpty()) {
            return List.of();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT data FROM question_bank WHERE path_id=? ORDER BY created_at DESC")) {
            ps.setString(1, pathId);
            try (ResultSet rs = ps.executeQuery()) {
                List<QuestionBankEntry> entries = new ArrayList<>();
                while (rs.next()) {
                    QuestionBankEntry entry = read(rs.getString("data"), QuestionBankEntry.class);
                    if (wanted.equals(entry.getQuestionId())) {
                        entries.add(entry);
                    }
                }
                return entries;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("read bank entries failed", e);
        }
    }

    List<PendingEvent> pendingEvents() {
        return events;
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse " + type.getSimpleName(), e);
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("cannot serialize " + value.getClass().getSimpleName(), e);
        }
    }

    /** 待落库的事件（提交成功后才写入）。 */
    record PendingEvent(String eventType, Map<String, Object> payload, String sessionId, String turnId) {
    }
}
