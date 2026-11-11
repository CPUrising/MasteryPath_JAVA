package com.masterypath.adapter.web;

import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.QuestionBankEntry;
import com.masterypath.domain.projection.QuestionBankProjector;
import com.masterypath.service.QuestionBankService;
import com.masterypath.storage.LearningStore;
import com.masterypath.storage.PreconditionFailedException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 题库工作台端点（{@code 0046}）：检索、标签管理、刷题与作答。
 *
 * <p>全部为增量端点，与既有的 {@code GET /questions}（{@link QuestionBankController}）并存。
 * 刷题端点使用白名单投影，MUST NOT 提前释放答案。
 */
@RestController
@RequestMapping("/questions")
public class QuestionBankPracticeController {

    private static final int DEFAULT_SEARCH_LIMIT = 100;
    private static final int MAX_SEARCH_LIMIT = 500;

    private final QuestionBankService bank;
    private final LearningStore store;

    public QuestionBankPracticeController(QuestionBankService bank, LearningStore store) {
        this.bank = bank;
        this.store = store;
    }

    /** 派生标签汇总（标签 + 题目数）。 */
    @GetMapping("/tags")
    public List<Map<String, Object>> tags(
            @RequestParam(name = "path_id", required = false) String pathId) {
        return bank.tagSummary(pathId);
    }

    /** 管理视图检索：含答案，用于题库列表与复盘。 */
    @GetMapping("/search")
    public List<Map<String, Object>> search(
            @RequestParam(name = "path_id", required = false) String pathId,
            @RequestParam(name = "tags", required = false) List<String> tags,
            @RequestParam(name = "result", required = false) String result,
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_SEARCH_LIMIT) int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset) {
        List<QuestionBankEntry> entries = bank.list(pathId, tags, result, keyword);
        int size = limit <= 0 ? DEFAULT_SEARCH_LIMIT : Math.min(limit, MAX_SEARCH_LIMIT);
        int from = Math.max(offset, 0);
        Map<String, String> names = pathNames();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = from; index < entries.size() && rows.size() < size; index++) {
            QuestionBankEntry entry = entries.get(index);
            Map<String, Object> row = QuestionBankController.entryMap(entry);
            row.put("path_name", nameOf(names, entry.getPathId()));
            rows.add(row);
        }
        return rows;
    }

    /** 刷题队列：隐藏答案，仅可确定性判分的题目。 */
    @GetMapping("/practice")
    public List<Map<String, Object>> practice(
            @RequestParam(name = "path_id", required = false) String pathId,
            @RequestParam(name = "tags", required = false) List<String> tags,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "order", defaultValue = "newest") String order) {
        Map<String, String> names = pathNames();
        List<Map<String, Object>> cards = new ArrayList<>();
        for (QuestionBankEntry entry : bank.practiceQueue(pathId, tags, size, order)) {
            cards.add(QuestionBankProjector.practiceCard(entry, nameOf(names, entry.getPathId())));
        }
        return cards;
    }

    /** 提交一次刷题作答；只回写题库条目，不影响掌握闭环。 */
    @PostMapping("/{entryId}/attempt")
    public Map<String, Object> attempt(@PathVariable String entryId,
                                       @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        try {
            return bank.attempt(entryId, ApiSupport.str(body.get("answer")));
        } catch (PreconditionFailedException e) {
            throw new ApiException(404, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ApiException(422, e.getMessage());
        }
    }

    /** 重命名标签（工作区全量）；目标已存在时自然合并。 */
    @PostMapping("/tags/rename")
    public Map<String, Object> renameTag(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        return tagMutation(() -> bank.renameTag(
                ApiSupport.str(body.get("from")), ApiSupport.str(body.get("to"))));
    }

    /** 合并多个标签到同一个目标标签。 */
    @PostMapping("/tags/merge")
    public Map<String, Object> mergeTags(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        return tagMutation(() -> bank.mergeTags(
                strings(body.get("from")), ApiSupport.str(body.get("to"))));
    }

    /** 删除标签（工作区全量）。 */
    @PostMapping("/tags/delete")
    public Map<String, Object> deleteTag(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        return tagMutation(() -> bank.deleteTag(ApiSupport.str(body.get("tag"))));
    }

    /** 批量加标 / 移除标签。 */
    @PostMapping("/tags/batch")
    public Map<String, Object> batchTag(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        List<QuestionBankEntry> updated;
        try {
            updated = bank.batchTag(strings(body.get("entry_ids")),
                    strings(body.get("add")), strings(body.get("remove")));
        } catch (PreconditionFailedException e) {
            throw new ApiException(404, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ApiException(422, e.getMessage());
        }
        Map<String, String> names = pathNames();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (QuestionBankEntry entry : updated) {
            Map<String, Object> row = QuestionBankController.entryMap(entry);
            row.put("path_name", nameOf(names, entry.getPathId()));
            rows.add(row);
        }
        return ApiSupport.payload("updated", rows);
    }

    // -- 辅助 -----------------------------------------------------------------

    private static Map<String, Object> tagMutation(java.util.function.IntSupplier action) {
        try {
            return ApiSupport.payload("updated", action.getAsInt());
        } catch (IllegalArgumentException e) {
            throw new ApiException(422, e.getMessage());
        }
    }

    private Map<String, String> pathNames() {
        Map<String, String> names = new HashMap<>();
        for (LearningProgress progress : store.listPaths()) {
            String name = progress.getName();
            names.put(progress.getPathId(),
                    name == null || name.isEmpty() ? progress.getPathId() : name);
        }
        return names;
    }

    private static String nameOf(Map<String, String> names, String pathId) {
        String name = names.get(pathId);
        return name == null || name.isEmpty() ? pathId : name;
    }

    /** JSON 数组 → 字符串列表（跳过 null；元素按 {@code String.valueOf} 转换）。 */
    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }
}
