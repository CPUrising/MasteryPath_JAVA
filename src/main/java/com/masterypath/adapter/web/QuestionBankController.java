package com.masterypath.adapter.web;

import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.QuestionBankEntry;
import com.masterypath.service.LearningService;
import com.masterypath.storage.LearningStore;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局题库视图（参考实现 {@code GET /questions}）。
 *
 * <p>单独成类是因为它的路径在根下，而 {@link LearningController} 挂在 {@code /learning} 前缀。
 *
 * <p>给定 {@code path_id} 时等价于该路径的题库；否则跨全部路径聚合，并附加 {@code path_name}。
 */
@RestController
public class QuestionBankController {

    private final LearningService service;
    private final LearningStore store;

    public QuestionBankController(LearningService service, LearningStore store) {
        this.service = service;
        this.store = store;
    }

    @GetMapping("/questions")
    public List<Map<String, Object>> allQuestions(
            @RequestParam(name = "path_id", required = false) String pathId,
            @RequestParam(name = "tag", required = false) String tag) {
        if (pathId != null && !pathId.isEmpty()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (QuestionBankEntry entry : service.listQuestions(pathId, tag)) {
                rows.add(entryMap(entry));
            }
            return rows;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LearningProgress progress : store.listPaths()) {
            String name = progress.getName() == null || progress.getName().isEmpty()
                    ? progress.getPathId() : progress.getName();
            for (QuestionBankEntry entry : store.listBankEntries(progress.getPathId())) {
                if (tag != null && !tag.isEmpty()
                        && (entry.getTags() == null || !entry.getTags().contains(tag))) {
                    continue;
                }
                Map<String, Object> row = entryMap(entry);
                row.put("path_name", name);
                rows.add(row);
            }
        }
        return rows;
    }

    /** 题库条目的 {@code model_dump()} 等价。 */
    private static Map<String, Object> entryMap(QuestionBankEntry entry) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("entry_id", entry.getEntryId());
        row.put("path_id", entry.getPathId());
        row.put("knowledge_point_id", entry.getKnowledgePointId());
        row.put("knowledge_point_name", entry.getKnowledgePointName());
        row.put("question_id", entry.getQuestionId());
        row.put("question", entry.getQuestion());
        row.put("question_type", entry.getQuestionType());
        row.put("options", entry.getOptions());
        row.put("correct_answer", entry.getCorrectAnswer());
        row.put("explanation", entry.getExplanation());
        row.put("difficulty", entry.getDifficulty());
        row.put("user_answer", entry.getUserAnswer());
        row.put("is_correct", entry.isCorrect());
        row.put("result", entry.getResult());
        row.put("source", entry.getSource());
        row.put("tags", entry.getTags());
        row.put("created_at", entry.getCreatedAt());
        row.put("updated_at", entry.getUpdatedAt());
        return row;
    }
}
