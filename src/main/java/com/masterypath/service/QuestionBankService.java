package com.masterypath.service;

import com.masterypath.domain.bank.QuestionBankGrading;
import com.masterypath.domain.bank.QuestionBankKnowledgeBases;
import com.masterypath.domain.grading.Decision;
import com.masterypath.domain.model.KnowledgeBaseRef;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.QuestionBankEntry;
import com.masterypath.storage.LearningStore;
import com.masterypath.storage.PreconditionFailedException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 题库工作台（{@code 0046}）：检索、标签管理与刷题。
 *
 * <p>与 {@link LearningService#tagQuestion} 的关系：那条路径是参考实现的行为等价物
 * （整体替换、允许空白、{@code null → "None"}），本类只做<b>增量</b>能力，不改既有语义。
 *
 * <p>铁律：刷题只回写题库条目的最近一次作答，不触碰任何 path 聚合状态。
 */
public class QuestionBankService {

    private static final int DEFAULT_PRACTICE_SIZE = 10;
    private static final int MAX_PRACTICE_SIZE = 50;
    private static final Set<String> WRONG_RESULTS = Set.of("incorrect", "partial", "voided");

    private final LearningStore store;

    public QuestionBankService(LearningStore store) {
        this.store = store;
    }

    /**
     * 题库检索：路径 + 多标签（AND）+ 结果 + 关键字 + 知识库（OR）。
     *
     * @param pathId  空白表示跨全部路径
     * @param tags    全部命中才保留（AND）；{@code null} 或空列表表示不过滤
     * @param result  {@code wrong} 表示 incorrect/partial/voided，其余精确匹配
     * @param keyword 对题干与知识点名做忽略大小写的包含匹配
     * @param kbIds   属于任一知识库即保留（OR）；{@code null} 或空列表表示不过滤
     */
    public List<QuestionBankEntry> list(String pathId, List<String> tags, String result,
                                        String keyword, List<String> kbIds) {
        List<QuestionBankEntry> entries = isBlank(pathId)
                ? store.listAllBankEntries()
                : store.listBankEntries(pathId);
        Map<String, LearningProgress> index = pathIndex();
        List<QuestionBankEntry> filtered = new ArrayList<>();
        String needle = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        for (QuestionBankEntry entry : entries) {
            enrich(entry, index);
            if (!matchesTags(entry, tags) || !matchesResult(entry, result)) {
                continue;
            }
            if (!QuestionBankKnowledgeBases.matches(entry.getKnowledgeBases(), kbIds)) {
                continue;
            }
            if (!needle.isEmpty() && !matchesKeyword(entry, needle)) {
                continue;
            }
            filtered.add(entry);
        }
        return filtered;
    }

    /**
     * 按知识库聚合题目数（{@code 0047}）。
     *
     * <p>多归属导致各组 {@code total} 之和 ≥ 总题数，这是预期语义。
     * 未关联组（{@code knowledge_base_id = ""}）排在最后，{@code name} 留空由前端本地化。
     */
    public List<Map<String, Object>> kbGroups(String pathId) {
        List<QuestionBankEntry> entries = list(pathId, List.of(), "", "", List.of());
        Map<String, KnowledgeBaseRef> refs = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        int unassigned = 0;
        for (QuestionBankEntry entry : entries) {
            if (entry.getKnowledgeBases().isEmpty()) {
                unassigned++;
                continue;
            }
            for (KnowledgeBaseRef ref : entry.getKnowledgeBases()) {
                refs.putIfAbsent(ref.id(), ref);
                counts.merge(ref.id(), 1, Integer::sum);
            }
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(item -> refs.get(item.getKey()).name()))
                .forEach(item -> groups.add(payload(
                        "knowledge_base_id", item.getKey(),
                        "name", refs.get(item.getKey()).name(),
                        "total", item.getValue())));
        if (unassigned > 0) {
            groups.add(payload("knowledge_base_id", "", "name", "", "total", unassigned));
        }
        return groups;
    }

    /** 派生标签汇总：忽略 null 与空白；按题目数降序、标签名升序。 */
    public List<Map<String, Object>> tagSummary(String pathId) {
        List<QuestionBankEntry> entries = isBlank(pathId)
                ? store.listAllBankEntries()
                : store.listBankEntries(pathId);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (QuestionBankEntry entry : entries) {
            for (String tag : entry.getTags()) {
                if (tag == null || tag.isBlank()) {
                    continue;
                }
                counts.merge(tag, 1, Integer::sum);
            }
        }
        List<Map<String, Object>> summary = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(item -> summary.add(payload(
                        "tag", item.getKey(), "count", item.getValue())));
        return summary;
    }

    /**
     * 重命名标签（工作区全量），{@code to} 已存在时自然合并并去重。
     *
     * @return 实际变更的条目数
     */
    public int renameTag(String from, String to) {
        if (isBlank(from) || isBlank(to)) {
            throw new IllegalArgumentException("tag name must not be blank");
        }
        int changed = 0;
        for (QuestionBankEntry entry : store.listAllBankEntries()) {
            if (!entry.getTags().contains(from)) {
                continue;
            }
            List<String> replaced = replaceTags(entry.getTags(), Set.of(from), to);
            if (mutateIfChanged(entry, replaced)) {
                changed++;
            }
        }
        return changed;
    }

    /**
     * 合并多个标签到 {@code to}。
     *
     * @return 实际变更的条目数
     */
    public int mergeTags(List<String> from, String to) {
        if (from == null || from.isEmpty()) {
            throw new IllegalArgumentException("tags to merge must not be empty");
        }
        if (isBlank(to)) {
            throw new IllegalArgumentException("tag name must not be blank");
        }
        Set<String> sources = new LinkedHashSet<>(from);
        int changed = 0;
        for (QuestionBankEntry entry : store.listAllBankEntries()) {
            boolean hit = entry.getTags().stream().anyMatch(sources::contains);
            if (!hit) {
                continue;
            }
            List<String> replaced = replaceTags(entry.getTags(), sources, to);
            if (mutateIfChanged(entry, replaced)) {
                changed++;
            }
        }
        return changed;
    }

    /**
     * 删除标签（工作区全量）。
     *
     * @return 实际变更的条目数
     */
    public int deleteTag(String tag) {
        if (isBlank(tag)) {
            throw new IllegalArgumentException("tag name must not be blank");
        }
        int changed = 0;
        for (QuestionBankEntry entry : store.listAllBankEntries()) {
            if (!entry.getTags().contains(tag)) {
                continue;
            }
            List<String> remaining = new ArrayList<>(entry.getTags());
            remaining.removeIf(tag::equals);
            if (mutateIfChanged(entry, remaining)) {
                changed++;
            }
        }
        return changed;
    }

    /**
     * 批量打标：先 add 后 remove，去重保序。
     *
     * @throws PreconditionFailedException 任一 entryId 不存在
     */
    public List<QuestionBankEntry> batchTag(List<String> entryIds, List<String> add,
                                            List<String> remove) {
        if (entryIds == null || entryIds.isEmpty()) {
            throw new IllegalArgumentException("entry_ids must not be empty");
        }
        Set<String> additions = add == null ? Set.of() : new LinkedHashSet<>(add);
        Set<String> removals = remove == null ? Set.of() : new HashSet<>(remove);
        List<QuestionBankEntry> updated = new ArrayList<>();
        for (String entryId : entryIds) {
            QuestionBankEntry entry = store.mutateBankEntry(entryId, current -> {
                Set<String> tags = new LinkedHashSet<>(current.getTags());
                for (String tag : additions) {
                    if (tag != null) {
                        tags.add(tag);
                    }
                }
                tags.removeAll(removals);
                current.setTags(new ArrayList<>(tags));
                return current;
            });
            if (entry == null) {
                throw new PreconditionFailedException("unknown question bank entry: " + entryId);
            }
            updated.add(entry);
        }
        return updated;
    }

    /**
     * 刷题队列：只保留可确定性判分的题目。
     *
     * @param kbIds  属于任一知识库即保留（OR）；空表示不过滤
     * @param size   默认 10，夹取 1–50
     * @param order  {@code newest}（默认）/ {@code wrong_first} / {@code random}
     */
    public List<QuestionBankEntry> practiceQueue(String pathId, List<String> tags,
                                                 List<String> kbIds, Integer size, String order) {
        List<QuestionBankEntry> candidates = new ArrayList<>(
                list(pathId, tags, "", "", kbIds));
        candidates.removeIf(entry -> !QuestionBankGrading.practiceable(entry));
        String sorting = order == null ? "" : order.trim().toLowerCase(Locale.ROOT);
        if ("wrong_first".equals(sorting)) {
            candidates.sort(Comparator.comparingInt(entry -> entry.isCorrect() ? 1 : 0));
        } else if ("random".equals(sorting)) {
            Collections.shuffle(candidates);
        }
        int limit = size == null || size <= 0
                ? DEFAULT_PRACTICE_SIZE : Math.min(size, MAX_PRACTICE_SIZE);
        if (candidates.size() > limit) {
            return new ArrayList<>(candidates.subList(0, limit));
        }
        return candidates;
    }

    /**
     * 提交一次刷题作答。
     *
     * <p>只写题库条目的 {@code user_answer} / {@code is_correct} / {@code result}；
     * {@code ungraded} 时零写入。不得触碰掌握度、证据、待答题或复习队列。
     */
    public Map<String, Object> attempt(String entryId, String answer) {
        QuestionBankEntry entry = store.getBankEntry(entryId);
        if (entry == null) {
            throw new PreconditionFailedException("unknown question bank entry: " + entryId);
        }
        if (!QuestionBankGrading.practiceable(entry)) {
            throw new IllegalArgumentException("question is not practiceable: " + entryId);
        }
        String submitted = answer == null ? "" : answer;
        Decision decision = QuestionBankGrading.grade(entry, submitted);
        if (decision.isUngraded()) {
            return payload("status", "ungraded", "entry_id", entryId, "reason", decision.reason());
        }
        store.mutateBankEntry(entryId, current -> {
            current.setUserAnswer(submitted);
            current.setCorrect(decision.isCorrect());
            current.setResult(decision.result());
            return current;
        });
        return payload(
                "status", "graded",
                "entry_id", entryId,
                "result", decision.result(),
                "is_correct", decision.isCorrect(),
                "correct_answer", entry.getCorrectAnswer(),
                "explanation", entry.getExplanation(),
                "reason", decision.reason());
    }

    // -- 内部 -----------------------------------------------------------------

    /** 单次调用内构建 {@code pathId → 路径聚合} 索引，避免逐条查库。 */
    private Map<String, LearningProgress> pathIndex() {
        Map<String, LearningProgress> index = new LinkedHashMap<>();
        for (LearningProgress progress : store.listPaths()) {
            index.put(progress.getPathId(), progress);
        }
        return index;
    }

    /** 回填知识库归属；路径不存在时置空列表（未关联）。 */
    private static void enrich(QuestionBankEntry entry, Map<String, LearningProgress> index) {
        entry.setKnowledgeBases(
                QuestionBankKnowledgeBases.of(index.get(entry.getPathId())));
    }

    private static boolean matchesTags(QuestionBankEntry entry, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return true;
        }
        for (String tag : tags) {
            if (!entry.getTags().contains(tag)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesResult(QuestionBankEntry entry, String result) {
        if (isBlank(result)) {
            return true;
        }
        String expected = result.trim().toLowerCase(Locale.ROOT);
        if ("wrong".equals(expected)) {
            return WRONG_RESULTS.contains(entry.getResult());
        }
        return expected.equals(entry.getResult());
    }

    private static boolean matchesKeyword(QuestionBankEntry entry, String needle) {
        String question = entry.getQuestion() == null
                ? "" : entry.getQuestion().toLowerCase(Locale.ROOT);
        String point = entry.getKnowledgePointName() == null
                ? "" : entry.getKnowledgePointName().toLowerCase(Locale.ROOT);
        return question.contains(needle) || point.contains(needle);
    }

    /** 把 {@code sources} 中的标签替换为 {@code target}（写入首次命中位置），整体去重保序。 */
    private static List<String> replaceTags(List<String> tags, Set<String> sources, String target) {
        LinkedHashSet<String> replaced = new LinkedHashSet<>();
        for (String tag : tags) {
            if (sources.contains(tag)) {
                replaced.add(target);
            } else {
                replaced.add(tag);
            }
        }
        return new ArrayList<>(replaced);
    }

    private boolean mutateIfChanged(QuestionBankEntry entry, List<String> tags) {
        if (entry.getTags().equals(tags)) {
            return false;
        }
        return store.mutateBankEntry(entry.getEntryId(), current -> {
            current.setTags(tags);
            return current;
        }) != null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }
}
