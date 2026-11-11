package com.masterypath.domain.bank;

import com.masterypath.domain.model.KnowledgeBaseRef;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.TopicSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 题库的知识库归属解析（{@code 0047}）。
 *
 * <p><b>纯函数层</b>：零 I/O、零 Spring。路径绑定的知识库不是独立列，而是
 * {@code sources[]} 中 {@code kind == "knowledge_base"} 的条目——解析规则与
 * {@code LearningService.getState} 的 {@code knowledge_bases} 保持一致。
 *
 * <p>一条路径可绑定多个知识库，因此一道题<b>同时属于</b>这些知识库（多对多）。
 */
public final class QuestionBankKnowledgeBases {

    private static final String KNOWLEDGE_BASE_KIND = "knowledge_base";

    private QuestionBankKnowledgeBases() {
    }

    /**
     * 路径绑定的知识库，按 id 去重并保持首次出现顺序。
     *
     * @param progress 路径聚合；{@code null}（路径已删除）返回空列表
     */
    public static List<KnowledgeBaseRef> of(LearningProgress progress) {
        if (progress == null || progress.getSources() == null) {
            return List.of();
        }
        Map<String, KnowledgeBaseRef> byId = new LinkedHashMap<>();
        for (TopicSource source : progress.getSources()) {
            if (source == null || !KNOWLEDGE_BASE_KIND.equals(source.getKind())) {
                continue;
            }
            String id = source.getSourceId() == null || source.getSourceId().isBlank()
                    ? source.getId() : source.getSourceId();
            if (id == null || id.isBlank()) {
                continue;
            }
            String name = source.getLabel() == null || source.getLabel().isBlank()
                    ? id : source.getLabel();
            byId.putIfAbsent(id, new KnowledgeBaseRef(id, name));
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * OR 语义：属于任一选中知识库即命中。
     *
     * @param refs   条目的知识库归属
     * @param filter 选中的知识库 id；{@code null} 或空列表表示不过滤
     */
    public static boolean matches(List<KnowledgeBaseRef> refs, List<String> filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        if (refs == null || refs.isEmpty()) {
            return false;
        }
        for (KnowledgeBaseRef ref : refs) {
            if (filter.contains(ref.id())) {
                return true;
            }
        }
        return false;
    }
}
