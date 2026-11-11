package com.masterypath.domain.projection;

import com.masterypath.domain.model.QuestionBankEntry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 刷题卡投影白名单：不得泄漏答案键（{@code INV-07}）。 */
class QuestionBankProjectorTest {

    @Test
    @DisplayName("恰好 11 个白名单键，且不含任何答案字段")
    void practiceCardWhitelist() {
        QuestionBankEntry entry = new QuestionBankEntry();
        entry.setEntryId("e1");
        entry.setPathId("p");
        entry.setKnowledgePointId("k1");
        entry.setKnowledgePointName("fact one");
        entry.setQuestion("2+2?");
        entry.setQuestionType("short");
        entry.setCorrectAnswer("4");
        entry.setExplanation("because");
        entry.setUserAnswer("4");
        entry.setDifficulty("easy");
        entry.setResult("correct");
        entry.setTags(List.of("math"));
        List<Map<String, Object>> options = new ArrayList<>();
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("label", "A");
        option.put("body", "4");
        option.put("extra", "leak");
        options.add(option);
        entry.setOptions(options);

        Map<String, Object> card = QuestionBankProjector.practiceCard(entry, "Path");

        assertThat(card).containsOnlyKeys(
                "entry_id", "path_id", "path_name", "knowledge_point_id",
                "knowledge_point_name", "question", "question_type", "options",
                "difficulty", "tags", "result", "knowledge_bases");
        assertThat(card).doesNotContainKeys(
                "correct_answer", "explanation", "user_answer", "is_correct");
        assertThat(card).containsEntry("path_name", "Path");
        assertThat(optionsOf(card).get(0)).containsOnlyKeys("label", "body");
    }

    @Test
    @DisplayName("路径名为空时回退 path_id")
    void pathNameFallsBack() {
        QuestionBankEntry entry = new QuestionBankEntry();
        entry.setEntryId("e1");
        entry.setPathId("p");
        assertThat(QuestionBankProjector.practiceCard(entry, ""))
                .containsEntry("path_name", "p");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> optionsOf(Map<String, Object> card) {
        return (List<Map<String, Object>>) card.get("options");
    }
}
