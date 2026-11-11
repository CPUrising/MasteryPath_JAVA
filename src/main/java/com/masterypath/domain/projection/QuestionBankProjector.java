package com.masterypath.domain.projection;

import com.masterypath.domain.model.QuestionBankEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 题库刷题卡投影（{@code 0046} / {@code INV-07}）。
 *
 * <p>白名单恰好 11 个键：{@code entry_id} / {@code path_id} / {@code path_name} /
 * {@code knowledge_point_id} / {@code knowledge_point_name} / {@code question} /
 * {@code question_type} / {@code options} / {@code difficulty} / {@code tags} / {@code result}。
 *
 * <p>MUST NOT 出现：{@code correct_answer}、{@code explanation}、{@code user_answer}、
 * {@code is_correct}。答案只在 {@code POST /questions/{entryId}/attempt} 之后释放。
 */
public final class QuestionBankProjector {

    private QuestionBankProjector() {
    }

    /** 把题库条目投影为刷题卡（隐藏答案）。 */
    public static Map<String, Object> practiceCard(QuestionBankEntry entry, String pathName) {
        Map<String, Object> card = new LinkedHashMap<>();
        if (entry == null) {
            return card;
        }
        card.put("entry_id", entry.getEntryId());
        card.put("path_id", entry.getPathId());
        card.put("path_name", pathName == null || pathName.isEmpty()
                ? entry.getPathId() : pathName);
        card.put("knowledge_point_id", entry.getKnowledgePointId());
        card.put("knowledge_point_name", entry.getKnowledgePointName());
        card.put("question", entry.getQuestion());
        card.put("question_type", entry.getQuestionType());
        card.put("options", publicOptions(entry));
        card.put("difficulty", entry.getDifficulty());
        card.put("tags", new ArrayList<>(entry.getTags()));
        card.put("result", entry.getResult());
        card.put("knowledge_bases", entry.getKnowledgeBases());
        return card;
    }

    /** 选项投影：只保留 {@code label} 与 {@code body}。 */
    private static List<Map<String, Object>> publicOptions(QuestionBankEntry entry) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (Map<String, Object> option : entry.getOptions()) {
            Object label = option.get("label");
            Object body = option.get("body");
            Map<String, Object> projected = new LinkedHashMap<>();
            projected.put("label", label == null ? "" : String.valueOf(label));
            projected.put("body", body == null ? "" : String.valueOf(body));
            options.add(projected);
        }
        return options;
    }
}
