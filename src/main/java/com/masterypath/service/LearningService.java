package com.masterypath.service;

import com.masterypath.domain.model.DeferredObjective;
import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.LearnerMasteryOverride;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.TopicSource;
import com.masterypath.domain.policy.MasteryPolicy;
import com.masterypath.storage.LearningStore;
import com.masterypath.storage.PreconditionFailedException;
import com.masterypath.storage.Transaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务核心（参考实现 {@code learning/service.py} 的 {@code LearningService}）。
 *
 * <p>本类只做<b>编排</b>：每个写方法都包成 {@code store.mutate(pathId, fn)}，在回调里改聚合并
 * {@code tx.emit(...)}。业务判定一律委托给 {@link MasteryPolicy}，本类不重复实现门槛逻辑。
 *
 * <p>错误文案与事件 payload 逐字复刻参考实现，包括<b>不对称</b>之处：
 * {@link #overrideMastery} 用 {@code "unknown knowledge point: "}，而
 * {@link #deferObjective} / {@link #noteExplained} 用 {@code "unknown objective: "}。
 */
public final class LearningService {

    private final LearningStore store;

    public LearningService(LearningStore store) {
        this.store = store;
    }

    /** 暴露给上层（{@code 0007} 起）读取聚合，避免各处重复持有 store。 */
    public LearningStore store() {
        return store;
    }

    // -- 路径生命周期 ---------------------------------------------------------

    /**
     * 创建路径；已存在则拒绝。
     *
     * <p>与参考实现一致：存在性检查在事务之外，不额外加锁（会留下 TOCTOU 窗口，属已知行为）。
     *
     * @throws PreconditionFailedException 路径已存在
     */
    public LearningProgress createPath(String pathId, String goal, String name) {
        if (store.load(pathId) != null) {
            throw new PreconditionFailedException("path already exists: " + pathId);
        }
        return store.mutate(pathId, tx -> {
            tx.progress().setGoal(goal == null ? "" : goal);
            tx.progress().setName(name == null ? "" : name);
            tx.emit("path.created", payload(
                    "goal", tx.progress().getGoal(),
                    "name", tx.progress().getName()));
            return tx.progress();
        }, null, true);
    }

    /** 改名。参考实现不校验空名，这里照抄。 */
    public Map<String, Object> rename(String pathId, String name) {
        return store.mutate(pathId, tx -> {
            tx.progress().setName(name == null ? "" : name);
            tx.emit("path.renamed", payload("name", tx.progress().getName()));
            return payload("status", "renamed", "name", tx.progress().getName());
        });
    }

    /** 删除路径及其从属数据；不存在返回 {@code false}。 */
    public boolean delete(String pathId) {
        return store.deletePath(pathId);
    }

    // -- 来源附加 -------------------------------------------------------------

    /**
     * 整体替换路径的来源列表（例如扫描文件夹后）。
     *
     * @return 替换后的全量来源（序列化为 map）
     */
    public List<Map<String, Object>> attachSources(String pathId, List<TopicSource> sources) {
        List<TopicSource> incoming = sources == null ? List.of() : List.copyOf(sources);
        return store.mutate(pathId, tx -> {
            tx.progress().setSources(new ArrayList<>(incoming));
            tx.emit("sources.attached", payload("count", incoming.size()));
            return dumpSources(tx.progress().getSources());
        }, null, true);
    }

    /**
     * 只替换 {@code kind == "knowledge_base"} 的来源，其余类别原样保留并排在前面。
     *
     * <p>与 {@link #attachSources} 的区别就在这里：UI 增删知识库时不能清掉用户已附加的文件来源。
     *
     * @return 合并后的全量来源（序列化为 map）
     */
    public List<Map<String, Object>> setKnowledgeBaseSources(String pathId, List<TopicSource> sources) {
        List<TopicSource> incoming = sources == null ? List.of() : List.copyOf(sources);
        return store.mutate(pathId, tx -> {
            List<TopicSource> others = new ArrayList<>();
            for (TopicSource source : tx.progress().getSources()) {
                if (!"knowledge_base".equals(source.getKind())) {
                    others.add(source);
                }
            }
            others.addAll(incoming);
            tx.progress().setSources(others);
            tx.emit("sources.knowledge_bases", payload("count", incoming.size()));
            return dumpSources(tx.progress().getSources());
        }, null, true);
    }

    // -- 目标状态 -------------------------------------------------------------

    /**
     * 学习者声明掌握：推进前沿，<b>不写分数、不产证据</b>（{@code INV-01} / {@code INV-15}）。
     *
     * <p>{@code gateCleared} 必须在写入 override <b>之后</b>计算——{@code isMastered} 读的就是
     * override，先算后写会让它恒为 {@code false}。
     *
     * <p>已知缺口：参考实现在 {@code gateCleared} 时还会播种复习计划并发 {@code review.scheduled}。
     * 该能力属 {@code 0010}（SM-2 调度），本 change 不实现。
     *
     * @return {@code knowledge_point_id} / {@code source="learner"} / {@code gate_cleared}
     */
    public Map<String, Object> overrideMastery(String pathId, String kpId, String note) {
        String safeNote = note == null ? "" : note;
        return store.mutate(pathId, tx -> {
            LearningProgress progress = tx.progress();
            LearningProgress.KnowledgePointLocation location = progress.findKnowledgePoint(kpId);
            tx.require(location.found(), "unknown knowledge point: " + kpId);
            KnowledgePoint kp = location.knowledgePoint();

            boolean wasMastered = MasteryPolicy.isMastered(progress, kp);
            progress.getLearnerMasteryOverrides()
                    .put(kpId, new LearnerMasteryOverride(kpId, safeNote));
            boolean gateCleared = MasteryPolicy.isMastered(progress, kp) && !wasMastered;

            tx.emit("mastery.override", payload(
                    "knowledge_point_id", kpId,
                    "note", safeNote));
            return payload(
                    "knowledge_point_id", kpId,
                    "source", "learner",
                    "gate_cleared", gateCleared);
        });
    }

    /**
     * 延后一个目标：写入 {@code deferredObjectives}，该 KP 不再被 {@code nextObjective} 选中。
     *
     * <p>事件 payload <b>不含</b> {@code note}（与 {@link #noteExplained} 不对称，属参考实现原样行为）。
     */
    public Map<String, Object> deferObjective(String pathId, String kpId, String note) {
        String safeNote = note == null ? "" : note;
        return store.mutate(pathId, tx -> {
            LearningProgress progress = tx.progress();
            tx.require(progress.findKnowledgePoint(kpId).found(), "unknown objective: " + kpId);
            progress.getDeferredObjectives()
                    .put(kpId, new DeferredObjective(kpId, safeNote));
            tx.emit("objective.deferred", payload("knowledge_point_id", kpId));
            return payload("knowledge_point_id", kpId, "deferred", true);
        });
    }

    /**
     * 记录「已讲解」：只写 {@code explainedObjectives}，<b>不给掌握学分</b>。
     *
     * <p>事件 payload 含 {@code summary}（与 {@link #deferObjective} 不对称）。
     */
    public Map<String, Object> noteExplained(String pathId, String kpId, String summary) {
        String safeSummary = summary == null ? "" : summary;
        return store.mutate(pathId, tx -> {
            LearningProgress progress = tx.progress();
            tx.require(progress.findKnowledgePoint(kpId).found(), "unknown objective: " + kpId);
            progress.getExplainedObjectives().put(kpId, payload("summary", safeSummary));
            tx.emit("objective.explained", payload(
                    "knowledge_point_id", kpId,
                    "summary", safeSummary));
            return payload("knowledge_point_id", kpId, "mastery_credit", false);
        });
    }

    // -- 内部 ----------------------------------------------------------------

    /** 构造保持插入顺序的 payload；键值交替传入。 */
    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            map.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return map;
    }

    /** 来源序列化：字段名与参考实现 {@code model_dump()} 一致（snake_case）。 */
    private static List<Map<String, Object>> dumpSources(List<TopicSource> sources) {
        List<Map<String, Object>> dumped = new ArrayList<>();
        for (TopicSource source : sources) {
            dumped.add(payload(
                    "id", source.getId(),
                    "kind", source.getKind(),
                    "source_id", source.getSourceId(),
                    "label", source.getLabel(),
                    "excerpt", source.getExcerpt(),
                    "position", source.getPosition(),
                    "available", source.isAvailable(),
                    "metadata", source.getMetadata()));
        }
        return dumped;
    }
}
