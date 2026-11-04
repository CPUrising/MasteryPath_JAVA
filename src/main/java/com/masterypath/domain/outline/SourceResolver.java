package com.masterypath.domain.outline;

import com.masterypath.domain.model.TopicSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 来源标签解析（对应参考实现 {@code knowledge/loader.py} 的
 * {@code build_source_map} / {@code selected_source_ids}）。
 *
 * <p><b>纯函数层</b>：零 I/O。模型只能看到标签（如 {@code "a.md"}），
 * 但聚合里存的是来源 id，本类负责这层映射。
 *
 * <p>容错规则：先按完整标签匹配；若同一**基名**（{@code Path(label).name}）只对应一个来源，
 * 再按基名匹配。重名时不猜，避免把来源接错。
 */
public final class SourceResolver {

    /** 空解析器：任何标签都解析为空串（等价于参考实现的 {@code source_map=None} 之外的情形）。 */
    public static final SourceResolver EMPTY = new SourceResolver(Map.of(), Set.of());

    private final Map<String, String> labelToId;
    private final Set<String> selectedIds;

    private SourceResolver(Map<String, String> labelToId, Set<String> selectedIds) {
        this.labelToId = labelToId;
        this.selectedIds = selectedIds;
    }

    /** 由来源列表构造：{@code {label: id}} + 唯一基名别名。 */
    public static SourceResolver fromSources(List<TopicSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return EMPTY;
        }
        Map<String, String> mapping = new LinkedHashMap<>();
        Map<String, List<String>> basenames = new LinkedHashMap<>();
        Set<String> ids = new LinkedHashSet<>();
        for (TopicSource source : sources) {
            String id = source.getId();
            if (id == null || id.isEmpty()) {
                continue;
            }
            ids.add(id);
            mapping.put(source.getLabel(), id);
            String label = source.getLabel() == null ? "" : source.getLabel();
            String base = basename(label);
            basenames.computeIfAbsent(base, key -> new ArrayList<>()).add(id);
        }
        for (Map.Entry<String, List<String>> entry : basenames.entrySet()) {
            if (entry.getValue().size() == 1) {
                mapping.putIfAbsent(entry.getKey(), entry.getValue().get(0));
            }
        }
        return new SourceResolver(Map.copyOf(mapping), Set.copyOf(ids));
    }

    /** 显式指定映射（测试与显式覆盖用）。 */
    public static SourceResolver of(Map<String, String> labelToId, Set<String> selectedIds) {
        return new SourceResolver(
                labelToId == null ? Map.of() : Map.copyOf(labelToId),
                selectedIds == null ? Set.of() : Set.copyOf(selectedIds));
    }

    /**
     * 把标签解析为来源 id。
     *
     * @return 命中返回 id；未命中返回空串（调用方据此丢弃该来源）
     */
    public String resolve(String label) {
        if (label == null) {
            return "";
        }
        String key = label.trim();
        String resolved = labelToId.get(key);
        return resolved == null ? "" : resolved;
    }

    /** 已选中的来源 id 集合（缺口检测的「全集」）。 */
    public Set<String> selectedIds() {
        return selectedIds;
    }

    /** 批量解析并丢弃未命中的项（保持顺序）。 */
    public List<String> resolveAll(List<String> labels) {
        List<String> resolved = new ArrayList<>();
        if (labels == null) {
            return resolved;
        }
        for (String label : labels) {
            String id = resolve(label);
            if (!id.isEmpty()) {
                resolved.add(id);
            }
        }
        return resolved;
    }

    /** 取路径的最后一段，等价于 Python 的 {@code Path(label).name}。 */
    private static String basename(String label) {
        String normalised = label.replace('\\', '/');
        int slash = normalised.lastIndexOf('/');
        return slash < 0 ? normalised : normalised.substring(slash + 1);
    }
}
