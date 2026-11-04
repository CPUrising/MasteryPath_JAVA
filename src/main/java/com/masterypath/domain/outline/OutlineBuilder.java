package com.masterypath.domain.outline;

import com.masterypath.domain.model.CoverageGap;
import com.masterypath.domain.model.CoverageReport;
import com.masterypath.domain.model.GapKind;
import com.masterypath.domain.model.GapResolution;
import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningModule;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.ObjectiveProposal;
import com.masterypath.domain.model.QuizAttempt;
import com.masterypath.domain.model.SourceGap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 大纲构建与修订（spec 06 §二/§三，{@code ADR-004}/{@code ADR-011}）。
 *
 * <p><b>纯函数层</b>：零 I/O、零 LLM。接收 {@link LearningProgress} 与 payload，就地修改聚合并返回结果。
 *
 * <p>核心语义：
 * <ul>
 *   <li>{@link #build}：整图替换。非空路径必须 {@code confirm}，否则返回 {@code refused_rebuild}
 *       且<b>零状态变更</b>（{@code INV-41}）。</li>
 *   <li>{@link #revise}：按 id 的差分操作，未触动知识点的进度逐字段保留。</li>
 *   <li>两者都检出缺口（{@code INV-23}）并重算覆盖报告（{@code INV-56}）。</li>
 * </ul>
 *
 * <p><b>判定顺序不可调整</b>：{@code build} 必须先解析 payload 再判断 {@code confirm}，
 * 否则非法 payload 会返回 {@code refused_rebuild} 而不是抛错。
 */
public final class OutlineBuilder {

    /** 重建被拒绝时的提示（逐字复刻参考实现）。 */
    static final String REFUSED_REBUILD_MESSAGE =
            "This path already has an outline. Rebuilding resets all progress; "
                    + "pass confirm=true after telling the learner what will be lost.";

    private OutlineBuilder() {
    }

    // -- build ---------------------------------------------------------------

    /**
     * 创建或（带 {@code confirm}）替换大纲。
     *
     * @param progress          目标聚合，就地修改
     * @param payload           模型给出的 {@code {"modules": [...]}}
     * @param confirm           非空路径替换时必须为 {@code true}
     * @param sourceResolver    标签 → 来源 id；{@code null} 表示标签即 id
     * @param selectedSourceIds 已选中的来源 id（缺口与覆盖的全集）
     * @throws OutlineException payload 非法
     */
    public static BuildResult build(LearningProgress progress,
                                    Map<String, Object> payload,
                                    boolean confirm,
                                    SourceResolver sourceResolver,
                                    Set<String> selectedSourceIds) {
        // 顺序敏感：先解析（可能抛错），再判断是否拒绝重建。
        List<LearningModule> modules = parseBuildPayload(payload, sourceResolver);
        boolean hasContent = progress.getModules().stream()
                .anyMatch(module -> !module.getKnowledgePoints().isEmpty());
        Set<String> selected = selectedSourceIds == null ? Set.of() : Set.copyOf(selectedSourceIds);

        if (hasContent && !confirm) {
            List<String> dropped = new ArrayList<>();
            for (LearningModule module : progress.getModules()) {
                for (KnowledgePoint kp : module.getKnowledgePoints()) {
                    dropped.add(kp.getId());
                }
            }
            return BuildResult.refused(dropped, REFUSED_REBUILD_MESSAGE);
        }

        boolean replacing = hasContent;
        if (replacing) {
            resetLearningState(progress);
        }
        progress.setModules(new ArrayList<>(modules));
        for (LearningModule module : modules) {
            for (KnowledgePoint kp : module.getKnowledgePoints()) {
                progress.getKnowledgeTypes().put(kp.getId(), kp.getType());
            }
        }
        if (!modules.isEmpty()) {
            progress.setCurrentModuleId(modules.get(0).getId());
        }

        List<SourceGap> gaps = detectGaps(modules, selected, progress.getSourceGaps().size());
        progress.setSourceGaps(new ArrayList<>(gaps));
        progress.setCoverage(computeCoverage(progress, selected));
        return BuildResult.built(modules, gaps, replacing);
    }

    /**
     * 解析 build payload（两遍扫描）。
     *
     * <p>第一遍分配 {@code m1..mN} / {@code k1..kN} 并建立 {@code name → id}；第二遍解析前置——
     * 这样前置可以指向<b>后面</b>声明的知识点（前向引用）。
     */
    static List<LearningModule> parseBuildPayload(Map<String, Object> payload,
                                                  SourceResolver sourceResolver) {
        Object rawModules = payload == null ? null : payload.get("modules");
        if (!(rawModules instanceof List<?> moduleList) || moduleList.isEmpty()) {
            throw new OutlineException(
                    "mastery_build needs a non-empty 'modules' array: "
                            + "'modules': [{\"name\": \"...\", \"knowledge_points\": "
                            + "[{\"name\": \"...\", \"type\": \"memory|procedure|concept|design\"}]}]");
        }

        Set<String> names = new LinkedHashSet<>();
        List<ParsedModule> parsed = new ArrayList<>();
        int moduleCounter = 0;
        int kpCounter = 0;
        for (Object rawModule : moduleList) {
            if (!(rawModule instanceof Map<?, ?> moduleMap)) {
                throw new OutlineException("each module must be an object");
            }
            moduleCounter++;
            String moduleId = "m" + moduleCounter;
            Object rawKps = moduleMap.get("knowledge_points");
            List<?> kpSpecs;
            if (rawKps == null) {
                kpSpecs = List.of();
            } else if (rawKps instanceof List<?> list) {
                kpSpecs = list;
            } else {
                throw new OutlineException("module.knowledge_points must be an array");
            }
            List<ParsedKp> kps = new ArrayList<>();
            for (Object spec : kpSpecs) {
                if (!(spec instanceof Map<?, ?> specMap)) {
                    throw new OutlineException("each knowledge point must be an object");
                }
                String name = str(specMap.get("name")).trim();
                if (name.length() < 2) {
                    throw new OutlineException(
                            "knowledge point name must be at least 2 characters: '" + name + "'");
                }
                if (!names.add(name)) {
                    throw new OutlineException("duplicate knowledge point name: '" + name + "'");
                }
                KnowledgeType type = parseType(specMap.get("type"),
                        "knowledge point '" + name + "' has invalid type "
                                + "'" + str(specMap.get("type")) + "'");
                kpCounter++;
                kps.add(new ParsedKp("k" + kpCounter, name, type, specMap));
            }
            parsed.add(new ParsedModule(moduleId, moduleMap, kps));
        }

        Map<String, String> nameToId = new LinkedHashMap<>();
        for (ParsedModule module : parsed) {
            for (ParsedKp kp : module.kps()) {
                nameToId.put(kp.name(), kp.id());
            }
        }

        List<LearningModule> modules = new ArrayList<>();
        for (int order = 0; order < parsed.size(); order++) {
            ParsedModule module = parsed.get(order);
            List<KnowledgePoint> knowledgePoints = new ArrayList<>();
            for (ParsedKp spec : module.kps()) {
                List<String> prerequisiteIds = new ArrayList<>();
                for (Object prereq : asList(spec.raw().get("prerequisites"))) {
                    String prereqName = str(prereq).trim();
                    String resolved = nameToId.get(prereqName);
                    if (resolved == null) {
                        throw new OutlineException(
                                "knowledge point '" + spec.name() + "' references unknown prerequisite "
                                        + "'" + prereqName + "'");
                    }
                    prerequisiteIds.add(resolved);
                }
                List<String> sourceIds = resolveLabels(
                        asList(spec.raw().get("sources")), sourceResolver);

                KnowledgePoint point = new KnowledgePoint(
                        spec.id(), spec.name(), spec.type(), module.id());
                point.setPrerequisiteIds(prerequisiteIds);
                point.setSourceIds(sourceIds);
                knowledgePoints.add(point);
            }

            // 模块级 materials 接地：只补给未声明来源的知识点。
            List<String> moduleSources = resolveLabels(
                    asList(module.raw().get("materials")), sourceResolver);
            if (!moduleSources.isEmpty()) {
                for (KnowledgePoint point : knowledgePoints) {
                    if (point.getSourceIds().isEmpty()) {
                        point.setSourceIds(new ArrayList<>(moduleSources));
                    }
                }
            }

            LearningModule built = new LearningModule(
                    module.id(),
                    defaultIfBlank(str(module.raw().get("name")), module.id()),
                    order);
            built.setObjective(str(module.raw().get("objective")));
            built.setKnowledgePoints(knowledgePoints);
            modules.add(built);
        }
        return modules;
    }

    // -- coverage ------------------------------------------------------------

    /**
     * 计算覆盖报告（{@code ADR-018} / {@code INV-56}）。
     *
     * <p>确定性：不需要模型。{@code reported=false} 表示没有任何知识点声明来源，
     * 此时无法下结论，{@code missing} 保持为空而不是列出全部文档（{@code TC-N3}）。
     */
    public static CoverageReport computeCoverage(LearningProgress progress,
                                                 Set<String> selectedSourceIds) {
        Set<String> selected = selectedSourceIds == null ? Set.of() : Set.copyOf(selectedSourceIds);
        List<KnowledgePoint> allPoints = allKnowledgePoints(progress.getModules());

        Set<String> used = new LinkedHashSet<>();
        for (KnowledgePoint point : allPoints) {
            used.addAll(point.getSourceIds());
        }
        boolean reported = !used.isEmpty();

        int documents = selected.size();
        Set<String> coveredIds = new LinkedHashSet<>(selected);
        coveredIds.retainAll(used);
        int covered = coveredIds.size();

        List<CoverageGap> missing = new ArrayList<>();
        if (reported) {
            Set<String> unused = new TreeSet<>(selected);
            unused.removeAll(used);
            for (String sourceId : unused) {
                missing.add(new CoverageGap(sourceId, sourceId));
            }
        }

        long grounded = allPoints.stream()
                .filter(point -> !point.getSourceIds().isEmpty())
                .count();
        double groundingRatio = allPoints.isEmpty()
                ? 1.0
                : (double) grounded / allPoints.size();

        CoverageReport report = new CoverageReport();
        report.setDocuments(documents);
        report.setCovered(covered);
        report.setMissing(missing);
        report.setReported(reported);
        report.setGroundingRatio(groundingRatio);
        report.setAcyclic(isAcyclic(allPoints));
        return report;
    }

    /** 前置图是否无环：DFS 三色标记，只遍历图中存在的节点。 */
    static boolean isAcyclic(List<KnowledgePoint> points) {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (KnowledgePoint point : points) {
            graph.put(point.getId(), point.getPrerequisiteIds());
        }
        Map<String, Integer> state = new HashMap<>();
        for (String node : graph.keySet()) {
            if (!visit(node, graph, state)) {
                return false;
            }
        }
        return true;
    }

    private static boolean visit(String node, Map<String, List<String>> graph, Map<String, Integer> state) {
        Integer marker = state.get(node);
        if (marker != null && marker == 1) {
            return false;
        }
        if (marker != null && marker == 2) {
            return true;
        }
        state.put(node, 1);
        for (String neighbour : graph.getOrDefault(node, List.of())) {
            if (graph.containsKey(neighbour) && !visit(neighbour, graph, state)) {
                return false;
            }
        }
        state.put(node, 2);
        return true;
    }

    // -- gaps ----------------------------------------------------------------

    /**
     * 检出缺口：目标无来源 → {@code goal_gap}；选中来源未被使用 → {@code source_omission}。
     *
     * @param existingGapCount 续号起点（build 传已有缺口数，revise 传 0）
     */
    static List<SourceGap> detectGaps(List<LearningModule> modules,
                                      Set<String> selectedSourceIds,
                                      int existingGapCount) {
        List<SourceGap> gaps = new ArrayList<>();
        int counter = existingGapCount;

        Set<String> used = new LinkedHashSet<>();
        for (LearningModule module : modules) {
            for (KnowledgePoint point : module.getKnowledgePoints()) {
                used.addAll(point.getSourceIds());
                if (point.getSourceIds().isEmpty()) {
                    counter++;
                    gaps.add(gap("g" + counter, GapKind.GOAL_GAP, point.getId(),
                            "objective '" + point.getName() + "' has no supporting source"));
                }
            }
        }
        Set<String> unused = new TreeSet<>(selectedSourceIds == null ? Set.of() : selectedSourceIds);
        unused.removeAll(used);
        for (String sourceId : unused) {
            counter++;
            gaps.add(gap("g" + counter, GapKind.SOURCE_OMISSION, sourceId,
                    "selected source '" + sourceId + "' is not used by any module"));
        }
        return gaps;
    }

    private static SourceGap gap(String id, GapKind kind, String ref, String reason) {
        SourceGap gap = new SourceGap();
        gap.setId(id);
        gap.setKind(kind);
        gap.setRef(ref);
        gap.setReason(reason);
        return gap;
    }

    // -- gap resolution ------------------------------------------------------

    /**
     * 接受缺口（标记未接地）或记录一次重新生成尝试。
     *
     * @throws OutlineException 缺口不存在或 action 未知
     */
    public static SourceGap resolveGap(LearningProgress progress, String gapId, String action) {
        for (SourceGap gap : progress.getSourceGaps()) {
            if (gap.getId() == null || !gap.getId().equals(gapId)) {
                continue;
            }
            if ("accept".equals(action)) {
                gap.setResolution(GapResolution.ACCEPTED);
            } else if ("regenerate".equals(action)) {
                gap.setResolution(GapResolution.REGENERATED);
            } else {
                throw new OutlineException("unknown gap action: '" + action + "'");
            }
            return gap;
        }
        throw new OutlineException("unknown gap_id: '" + gapId + "'");
    }

    // -- proposals -----------------------------------------------------------

    /** 接受候选目标，使其成为知识点（只在 {@code pending} 时生效，{@code INV-54}）。 */
    public static ObjectiveProposal applyProposal(LearningProgress progress, String proposalId) {
        ObjectiveProposal proposal = findProposal(progress, proposalId);
        if (!"pending".equals(proposal.getStatus())) {
            return proposal;
        }
        LearningModule module = null;
        if (!proposal.getModuleId().isEmpty()) {
            for (LearningModule candidate : progress.getModules()) {
                if (candidate.getId().equals(proposal.getModuleId())) {
                    module = candidate;
                    break;
                }
            }
        }
        if (module == null && !progress.getModules().isEmpty()) {
            module = progress.getModules().get(0);
        }
        if (module == null) {
            module = new LearningModule(nextId("m", existingIds(progress)), "提议目标", 0);
            progress.getModules().add(module);
        }
        String kpId = nextId("k", existingIds(progress));
        KnowledgePoint point = new KnowledgePoint(
                kpId, proposal.getName(), proposal.getType(), module.getId());
        point.setSourceIds(new ArrayList<>(proposal.getSourceIds()));
        module.getKnowledgePoints().add(point);
        progress.getKnowledgeTypes().put(kpId, proposal.getType());
        proposal.setStatus("accepted");
        return proposal;
    }

    /** 拒绝候选目标（只在 {@code pending} 时生效）。 */
    public static ObjectiveProposal rejectProposal(LearningProgress progress, String proposalId) {
        ObjectiveProposal proposal = findProposal(progress, proposalId);
        if ("pending".equals(proposal.getStatus())) {
            proposal.setStatus("rejected");
        }
        return proposal;
    }

    private static ObjectiveProposal findProposal(LearningProgress progress, String proposalId) {
        for (ObjectiveProposal proposal : progress.getProposals()) {
            if (proposal.getId() != null && proposal.getId().equals(proposalId)) {
                return proposal;
            }
        }
        throw new OutlineException("unknown proposal: '" + proposalId + "'");
    }

    // -- revise --------------------------------------------------------------

    /**
     * 应用差分操作，未触动的知识点进度逐字段保留。
     *
     * <p>任一 op 抛错即中断；由于外层 {@code store.mutate} 有事务，整个 revise 会回滚，
     * 因此本层<b>不</b>自己做回滚。
     *
     * @param ops               操作列表，元素为 {@code {"op": "...", ...}}
     * @param selectedSourceIds 已选中的来源 id；缺口与覆盖**从 0 重算**（与 build 的续号不同）
     * @throws OutlineException 任一 op 非法
     */
    public static ReviseResult revise(LearningProgress progress,
                                      List<Map<String, Object>> ops,
                                      Set<String> selectedSourceIds) {
        if (ops == null) {
            throw new OutlineException("mastery_revise needs an 'ops' array");
        }
        List<String> applied = new ArrayList<>();
        List<String> droppedKpIds = new ArrayList<>();
        for (Map<String, Object> op : ops) {
            if (op == null) {
                throw new OutlineException("each op must be an object");
            }
            String name = str(op.get("op"));
            applyOp(progress, name, op, applied, droppedKpIds);
        }
        Set<String> selected = selectedSourceIds == null ? Set.of() : Set.copyOf(selectedSourceIds);
        List<SourceGap> gaps = detectGaps(progress.getModules(), selected, 0);
        progress.setSourceGaps(new ArrayList<>(gaps));
        progress.setCoverage(computeCoverage(progress, selected));
        return new ReviseResult(ReviseResult.REVISED, List.copyOf(applied),
                List.copyOf(droppedKpIds), List.copyOf(gaps), "");
    }

    private static void applyOp(LearningProgress progress, String name, Map<String, Object> op,
                                List<String> applied, List<String> droppedKpIds) {
        switch (name) {
            case "add_module" -> addModule(progress, op, applied);
            case "update_module" -> updateModule(progress, op, applied);
            case "remove_module" -> removeModule(progress, op, applied, droppedKpIds);
            case "add_kp" -> addKp(progress, op, applied);
            case "update_kp" -> updateKp(progress, op, applied);
            case "remove_kp" -> removeKp(progress, op, applied, droppedKpIds);
            case "move_kp" -> moveKp(progress, op, applied);
            case "reorder" -> reorder(progress, op, applied);
            default -> throw new OutlineException("unknown revise op: '" + name + "'");
        }
    }

    private static void addModule(LearningProgress progress, Map<String, Object> op,
                                  List<String> applied) {
        String moduleId = nextId("m", existingIds(progress));
        LearningModule module = new LearningModule(moduleId,
                defaultIfBlank(str(op.get("name")), moduleId),
                progress.getModules().size());
        module.setObjective(str(op.get("objective")));
        progress.getModules().add(module);
        applied.add("add_module:" + moduleId);
    }

    private static void updateModule(LearningProgress progress, Map<String, Object> op,
                                     List<String> applied) {
        LearningModule module = findModule(progress, str(op.get("module_id")));
        if (op.get("name") != null) {
            module.setName(str(op.get("name")));
        }
        if (op.get("objective") != null) {
            module.setObjective(str(op.get("objective")));
        }
        applied.add("update_module:" + module.getId());
    }

    private static void removeModule(LearningProgress progress, Map<String, Object> op,
                                     List<String> applied, List<String> droppedKpIds) {
        LearningModule module = findModule(progress, str(op.get("module_id")));
        for (KnowledgePoint point : module.getKnowledgePoints()) {
            dropKpState(progress, point.getId());
            droppedKpIds.add(point.getId());
        }
        progress.getModules().removeIf(candidate -> candidate.getId().equals(module.getId()));
        applied.add("remove_module:" + module.getId());
    }

    private static void addKp(LearningProgress progress, Map<String, Object> op,
                              List<String> applied) {
        LearningModule module = findModule(progress, str(op.get("module_id")));
        String name = str(op.get("name")).trim();
        if (name.length() < 2) {
            throw new OutlineException("new knowledge point name must be at least 2 characters");
        }
        KnowledgeType type;
        try {
            type = KnowledgeType.fromValue(op.get("type"));
        } catch (IllegalArgumentException e) {
            throw new OutlineException("invalid type for new kp '" + name + "'");
        }
        String kpId = nextId("k", existingIds(progress));
        KnowledgePoint point = new KnowledgePoint(kpId, name, type, module.getId());
        List<String> prerequisites = new ArrayList<>();
        for (Object prereq : asList(op.get("prerequisites"))) {
            prerequisites.add(str(prereq));
        }
        point.setPrerequisiteIds(prerequisites);
        module.getKnowledgePoints().add(point);
        progress.getKnowledgeTypes().put(kpId, type);
        applied.add("add_kp:" + kpId);
    }

    private static void updateKp(LearningProgress progress, Map<String, Object> op,
                                 List<String> applied) {
        KnowledgePoint point = findKp(progress, str(op.get("kp_id")));
        if (op.get("type") != null) {
            KnowledgeType requested;
            try {
                requested = KnowledgeType.fromValue(op.get("type"));
            } catch (IllegalArgumentException e) {
                throw new OutlineException("invalid type for kp '" + point.getId() + "'");
            }
            if (requested != point.getType()) {
                throw new OutlineException(
                        "knowledge point type is read-only (ADR-003); remove and re-add to change it");
            }
        }
        if (op.get("name") != null) {
            point.setName(str(op.get("name")));
        }
        if (op.get("prerequisites") != null) {
            List<String> prerequisites = new ArrayList<>();
            for (Object prereq : asList(op.get("prerequisites"))) {
                prerequisites.add(str(prereq));
            }
            point.setPrerequisiteIds(prerequisites);
        }
        applied.add("update_kp:" + point.getId());
    }

    private static void removeKp(LearningProgress progress, Map<String, Object> op,
                                 List<String> applied, List<String> droppedKpIds) {
        KnowledgePoint point = findKp(progress, str(op.get("kp_id")));
        LearningModule module = findModuleOf(progress, point.getId());
        dropKpState(progress, point.getId());
        module.getKnowledgePoints().removeIf(candidate -> candidate.getId().equals(point.getId()));
        droppedKpIds.add(point.getId());
        applied.add("remove_kp:" + point.getId());
    }

    private static void moveKp(LearningProgress progress, Map<String, Object> op,
                               List<String> applied) {
        KnowledgePoint point = findKp(progress, str(op.get("kp_id")));
        LearningModule source = findModuleOf(progress, point.getId());
        LearningModule target = findModule(progress, str(op.get("module_id")));
        source.getKnowledgePoints().removeIf(candidate -> candidate.getId().equals(point.getId()));
        point.setModuleId(target.getId());
        target.getKnowledgePoints().add(point);
        applied.add("move_kp:" + point.getId());
    }

    private static void reorder(LearningProgress progress, Map<String, Object> op,
                                List<String> applied) {
        LearningModule module = findModule(progress, str(op.get("module_id")));
        Map<String, Integer> order = new LinkedHashMap<>();
        int index = 0;
        for (Object kpId : asList(op.get("kp_ids"))) {
            order.put(str(kpId), index++);
        }
        Set<String> current = new LinkedHashSet<>();
        for (KnowledgePoint point : module.getKnowledgePoints()) {
            current.add(point.getId());
        }
        if (!current.equals(order.keySet())) {
            throw new OutlineException(
                    "reorder must list every knowledge point id exactly once");
        }
        module.getKnowledgePoints().sort(
                (left, right) -> Integer.compare(order.get(left.getId()), order.get(right.getId())));
        applied.add("reorder:" + module.getId());
    }

    private static LearningModule findModule(LearningProgress progress, String moduleId) {
        for (LearningModule module : progress.getModules()) {
            if (module.getId() != null && module.getId().equals(moduleId)) {
                return module;
            }
        }
        throw new OutlineException("unknown module_id: '" + moduleId + "'");
    }

    private static LearningModule findModuleOf(LearningProgress progress, String kpId) {
        for (LearningModule module : progress.getModules()) {
            for (KnowledgePoint point : module.getKnowledgePoints()) {
                if (point.getId() != null && point.getId().equals(kpId)) {
                    return module;
                }
            }
        }
        throw new OutlineException("unknown knowledge_point_id: '" + kpId + "'");
    }

    private static KnowledgePoint findKp(LearningProgress progress, String kpId) {
        for (LearningModule module : progress.getModules()) {
            for (KnowledgePoint point : module.getKnowledgePoints()) {
                if (point.getId() != null && point.getId().equals(kpId)) {
                    return point;
                }
            }
        }
        throw new OutlineException("unknown knowledge_point_id: '" + kpId + "'");
    }

    // -- shared helpers ------------------------------------------------------

    /**
     * 清空学习态但保留地图（重建时使用）。
     *
     * <p>清 7 个 map 与 3 个 list 的对应字段；漏清任一项都会让旧进度影响新大纲。
     */
    static void resetLearningState(LearningProgress progress) {
        progress.getMasteryLevels().clear();
        progress.getQualitativeMastery().clear();
        progress.getQuizAttempts().clear();
        progress.getLearningEvidence().clear();
        progress.getRepetitionStates().clear();
        progress.getReviewQueue().clear();
        progress.getLearnerMasteryOverrides().clear();
        progress.getDeferredObjectives().clear();
        progress.getExplainedObjectives().clear();
        progress.getKnowledgeTypes().clear();
        progress.setSourceGaps(new ArrayList<>());
        progress.setPendingQuestion(null);
        progress.setCurrentModuleId("");
        progress.setCurrentKpIndex(0);
    }

    /** 删除一个知识点的全部学习态（{@code remove_kp} / {@code remove_module} 使用）。 */
    static void dropKpState(LearningProgress progress, String kpId) {
        progress.getMasteryLevels().remove(kpId);
        progress.getQualitativeMastery().remove(kpId);
        progress.getKnowledgeTypes().remove(kpId);
        progress.getRepetitionStates().remove(kpId);
        progress.getLearnerMasteryOverrides().remove(kpId);
        progress.getDeferredObjectives().remove(kpId);
        progress.getExplainedObjectives().remove(kpId);

        List<QuizAttempt> attempts = new ArrayList<>();
        for (QuizAttempt attempt : progress.getQuizAttempts()) {
            if (!kpId.equals(attempt.getKnowledgePointId())) {
                attempts.add(attempt);
            }
        }
        progress.setQuizAttempts(attempts);

        progress.getLearningEvidence().removeIf(
                evidence -> kpId.equals(evidence.getKnowledgePointId()));
        progress.getReviewQueue().removeIf(
                task -> kpId.equals(task.getKnowledgePointId()));
    }

    /** 现有全部模块与知识点 id（用于分配新 id 时避让）。 */
    static Set<String> existingIds(LearningProgress progress) {
        Set<String> ids = new LinkedHashSet<>();
        for (LearningModule module : progress.getModules()) {
            ids.add(module.getId());
            for (KnowledgePoint point : module.getKnowledgePoints()) {
                ids.add(point.getId());
            }
        }
        return ids;
    }

    /** 分配 {@code prefix + n}，从 1 开始跳过已存在的 id。 */
    static String nextId(String prefix, Set<String> existing) {
        int index = 1;
        while (existing.contains(prefix + index)) {
            index++;
        }
        return prefix + index;
    }

    static List<KnowledgePoint> allKnowledgePoints(List<LearningModule> modules) {
        List<KnowledgePoint> points = new ArrayList<>();
        for (LearningModule module : modules) {
            points.addAll(module.getKnowledgePoints());
        }
        return points;
    }

    /** 解析来源标签，丢弃未命中的项（保持顺序）。 */
    private static List<String> resolveLabels(List<?> labels, SourceResolver resolver) {
        List<String> resolved = new ArrayList<>();
        if (labels == null || resolver == null) {
            // resolver 为 null 时标签即 id（参考实现 source_map=None 的行为）
            for (Object label : labels == null ? List.of() : labels) {
                String value = str(label);
                if (!value.isEmpty()) {
                    resolved.add(value);
                }
            }
            return resolved;
        }
        for (Object label : labels) {
            String id = resolver.resolve(str(label));
            if (!id.isEmpty()) {
                resolved.add(id);
            }
        }
        return resolved;
    }

    private static KnowledgeType parseType(Object raw, String errorMessage) {
        try {
            return KnowledgeType.fromValue(raw);
        } catch (IllegalArgumentException e) {
            throw new OutlineException(errorMessage);
        }
    }

    private static List<?> asList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return list;
        }
        return List.of(raw);
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** 解析中的模块中间态。 */
    private record ParsedModule(String id, Map<?, ?> raw, List<ParsedKp> kps) {
    }

    /** 解析中的知识点中间态。 */
    private record ParsedKp(String id, String name, KnowledgeType type, Map<?, ?> raw) {
    }
}
