package com.masterypath.domain.policy;

import com.masterypath.domain.model.AssessmentResult;
import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningEvidence;
import com.masterypath.domain.model.LearningModule;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.QuizAttempt;
import com.masterypath.domain.model.ReviewTask;
import com.masterypath.domain.model.TimeSupport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 掌握门槛与前沿推进（spec 01 §四 / {@code 03} §C）。
 *
 * <p><b>纯函数层</b>：零 I/O、零 Spring、不修改入参。所有判定读 {@link LearningProgress} 与
 * {@link KnowledgePoint}，返回布尔/字符串/{@link NextStep}。
 *
 * <p>核心语义（不可调换顺序，顺序即语义）：
 * <ol>
 *   <li>{@link #isAssessedMastered}：先查附加证据（{@code INV-28}），再按类型分支——
 *       定性类型看定性通过（{@code INV-05}），其余看分数 {@code >= 0.9}（{@code INV-04}）。</li>
 *   <li>{@link #masterySource}：{@code system}（评估通过）优先于 {@code learner}（声明）。</li>
 *   <li>{@link #isMastered}：来源非空即掌握（{@code INV-03}）。</li>
 *   <li>{@link #eligible}：前置全部已掌握才是硬门控放行（{@code INV-27}）。</li>
 *   <li>{@link #nextObjective}：待答题 &gt; 第一个可选未掌握 &gt; 到期复习 &gt; complete。</li>
 * </ol>
 */
public final class MasteryPolicy {

    /** 定量门槛：{@code memory}/{@code procedure} 必须达到此分数（ADR-007）。 */
    public static final Map<KnowledgeType, Double> QUANTITATIVE_GATE = Map.of(
            KnowledgeType.MEMORY, 0.9,
            KnowledgeType.PROCEDURE, 0.9);

    /** 定性类型：由 {@code mastery_assess} 通过判定，分数不参与。 */
    public static final Set<KnowledgeType> QUALITATIVE_TYPES =
            Set.of(KnowledgeType.CONCEPT, KnowledgeType.DESIGN);

    /** 定性通过时用于展示的掌握度。 */
    private static final double QUALITATIVE_PASS_DISPLAY = 1.0;

    private MasteryPolicy() {
    }

    // -- 查找 -----------------------------------------------------------------

    /**
     * 按 id 查找知识点及其所属模块。
     *
     * @return 命中的定位结果；找不到时 {@link KnowledgePointLocation#found()} 为 {@code false}
     */
    public static KnowledgePointLocation findKnowledgePoint(LearningProgress progress, String kpId) {
        for (LearningModule module : progress.getModules()) {
            for (KnowledgePoint kp : module.getKnowledgePoints()) {
                if (kp.getId() != null && kp.getId().equals(kpId)) {
                    return new KnowledgePointLocation(kp, module);
                }
            }
        }
        return KnowledgePointLocation.MISSING;
    }

    /** 知识点定位结果（等价于参考实现返回的 {@code (kp, module_id, module_name)}）。 */
    public record KnowledgePointLocation(KnowledgePoint knowledgePoint, LearningModule module) {

        public static final KnowledgePointLocation MISSING = new KnowledgePointLocation(null, null);

        public boolean found() {
            return knowledgePoint != null;
        }

        public String moduleId() {
            return module == null ? "" : module.getId();
        }

        public String moduleName() {
            return module == null ? "" : module.getName();
        }
    }

    // -- 门槛 -----------------------------------------------------------------

    /**
     * 该类型的门槛值。
     *
     * <p>定性类型返回 1.0 仅用于展示；判定不看它（走 {@code qualitativeMastery}）。
     */
    public static double gateThreshold(KnowledgeType type) {
        if (QUALITATIVE_TYPES.contains(type)) {
            return QUALITATIVE_PASS_DISPLAY;
        }
        return QUANTITATIVE_GATE.getOrDefault(type, 0.9);
    }

    /** 已达成「正确」的评估类型集合（跳过作废证据，{@code INV-28}）。 */
    public static Set<String> achievedEvidence(LearningProgress progress, String kpId) {
        Set<String> types = new HashSet<>();
        for (LearningEvidence evidence : progress.getLearningEvidence()) {
            if (kpId.equals(evidence.getKnowledgePointId())
                    && evidence.getResult() == AssessmentResult.CORRECT
                    && !evidence.isVoided()) {
                types.add(evidence.getAssessmentType().value());
            }
        }
        return types;
    }

    /** 该知识点的附加必需证据是否齐备（{@code INV-28}）。 */
    public static boolean evidenceSatisfied(LearningProgress progress, KnowledgePoint kp) {
        List<String> required = kp.getRequiredEvidence();
        if (required == null || required.isEmpty()) {
            return true;
        }
        return achievedEvidence(progress, kp.getId()).containsAll(required);
    }

    /** 仅凭证据是否已掌握（不含学习者声明）。 */
    public static boolean isAssessedMastered(LearningProgress progress, KnowledgePoint kp) {
        if (!evidenceSatisfied(progress, kp)) {
            return false;
        }
        if (QUALITATIVE_TYPES.contains(kp.getType())) {
            return Boolean.TRUE.equals(progress.getQualitativeMastery().get(kp.getId()));
        }
        double level = progress.getMasteryLevels().getOrDefault(kp.getId(), 0.0);
        return level >= gateThreshold(kp.getType());
    }

    /**
     * 掌握的来源：{@code system} | {@code learner} | {@code ""}。
     *
     * <p>{@code INV-01}：声明（learner）不写分数，因此这里只读不写。
     */
    public static String masterySource(LearningProgress progress, KnowledgePoint kp) {
        if (isAssessedMastered(progress, kp)) {
            return "system";
        }
        if (progress.getLearnerMasteryOverrides().containsKey(kp.getId())) {
            return "learner";
        }
        return "";
    }

    /** 路径是否可以越过该知识点（{@code INV-03}）。 */
    public static boolean isMastered(LearningProgress progress, KnowledgePoint kp) {
        return !masterySource(progress, kp).isEmpty();
    }

    /** 展示用掌握度：定性通过显示 1.0，否则读分数。 */
    public static double displayMastery(LearningProgress progress, KnowledgePoint kp) {
        if (QUALITATIVE_TYPES.contains(kp.getType())
                && Boolean.TRUE.equals(progress.getQualitativeMastery().get(kp.getId()))) {
            return QUALITATIVE_PASS_DISPLAY;
        }
        return progress.getMasteryLevels().getOrDefault(kp.getId(), 0.0);
    }

    /** 派生状态：{@code mastered} | {@code learning} | {@code new}。 */
    public static String objectiveStatus(LearningProgress progress, KnowledgePoint kp) {
        if (isMastered(progress, kp)) {
            return "mastered";
        }
        boolean seen = false;
        for (QuizAttempt attempt : progress.getQuizAttempts()) {
            if (kp.getId().equals(attempt.getKnowledgePointId())) {
                seen = true;
                break;
            }
        }
        if (!seen) {
            seen = progress.getQualitativeMastery().containsKey(kp.getId());
        }
        return seen ? "learning" : "new";
    }

    /**
     * 前置是否全部已掌握（硬门控，{@code INV-27}）。
     *
     * <p><b>fail-closed</b>：前置 id 无法解析时视为未满足——历史数据可能有悬空引用，
     * 此时宁可让目标不可选，也不能放行。
     */
    public static boolean eligible(LearningProgress progress, KnowledgePoint kp) {
        List<String> prerequisites = kp.getPrerequisiteIds();
        if (prerequisites == null || prerequisites.isEmpty()) {
            return true;
        }
        for (String prereqId : prerequisites) {
            KnowledgePointLocation location = findKnowledgePoint(progress, prereqId);
            if (!location.found() || !isMastered(progress, location.knowledgePoint())) {
                return false;
            }
        }
        return true;
    }

    /** 非作废的作答记录（{@code INV-14}：作废记录不计任何账）。 */
    public static List<QuizAttempt> activeAttempts(LearningProgress progress, String kpId) {
        List<QuizAttempt> active = new ArrayList<>();
        for (QuizAttempt attempt : progress.getQuizAttempts()) {
            if (kpId.equals(attempt.getKnowledgePointId()) && !attempt.isVoided()) {
                active.add(attempt);
            }
        }
        return active;
    }

    /**
     * 拒绝修改已存在知识点的类型（ADR-003 / {@code INV-06}）。
     *
     * @throws IllegalArgumentException 当新旧类型不一致
     */
    public static void requireTypeUnchanged(KnowledgePoint existing, KnowledgeType newType) {
        if (newType != existing.getType()) {
            throw new IllegalArgumentException(
                    "knowledge point type is read-only (ADR-003); remove and re-add the objective to change it");
        }
    }

    // -- 复习 -----------------------------------------------------------------

    /** 到期的复习任务，按遗忘风险降序、到期时间升序（{@code 04} §八）。 */
    public static List<ReviewTask> dueReviews(LearningProgress progress, double now) {
        List<ReviewTask> due = new ArrayList<>();
        for (ReviewTask task : progress.getReviewQueue()) {
            if (task.getDueAt() <= now) {
                due.add(task);
            }
        }
        due.sort(Comparator.comparingDouble(ReviewTask::getForgettingRisk).reversed()
                .thenComparingDouble(ReviewTask::getDueAt));
        return due;
    }

    /** 以当前时间计算到期复习。 */
    public static List<ReviewTask> dueReviews(LearningProgress progress) {
        return dueReviews(progress, TimeSupport.nowSeconds());
    }

    // -- 推进 -----------------------------------------------------------------

    /**
     * 下一个该做什么——由掌握门槛决定，而不是阶段游标。
     *
     * <p>优先级（顺序即语义，不可调换）：
     * <ol>
     *   <li>存在待答题目 → {@code answer_pending}（{@code INV-02}：单槽位必须先清空）</li>
     *   <li>按模块顺序、KP 顺序找到第一个「未掌握 + 未延后 + 前置已满足」的目标 →
     *       {@code assess}（定性）或 {@code practice}（定量）</li>
     *   <li>存在到期复习 → {@code review}</li>
     *   <li>否则 → {@code complete}</li>
     * </ol>
     */
    public static NextStep nextObjective(LearningProgress progress, double now) {
        if (progress.getPendingQuestion() != null) {
            return NextStep.answerPending(
                    progress.getPendingQuestion().getKnowledgePointId(),
                    progress.getPendingQuestion().getPrompt());
        }

        for (LearningModule module : orderedModules(progress)) {
            for (KnowledgePoint kp : module.getKnowledgePoints()) {
                if (isMastered(progress, kp)) {
                    continue;
                }
                if (progress.getDeferredObjectives().containsKey(kp.getId())) {
                    continue;
                }
                if (!eligible(progress, kp)) {
                    continue;
                }
                String action = QUALITATIVE_TYPES.contains(kp.getType()) ? "assess" : "practice";
                return new NextStep(
                        action,
                        module.getId(),
                        module.getName(),
                        kp.getId(),
                        kp.getName(),
                        kp.getType().value(),
                        objectiveStatus(progress, kp),
                        displayMastery(progress, kp),
                        gateThreshold(kp.getType()),
                        "next eligible objective",
                        "");
            }
        }

        List<ReviewTask> due = dueReviews(progress, now);
        if (!due.isEmpty()) {
            ReviewTask task = due.get(0);
            KnowledgePointLocation location = findKnowledgePoint(progress, task.getKnowledgePointId());
            return new NextStep(
                    "review",
                    location.moduleId(),
                    location.moduleName(),
                    task.getKnowledgePointId(),
                    location.found() ? location.knowledgePoint().getName() : "",
                    task.getKnowledgeType().value(),
                    "mastered",
                    location.found() ? displayMastery(progress, location.knowledgePoint()) : 1.0,
                    gateThreshold(task.getKnowledgeType()),
                    "spaced review due",
                    "");
        }

        return NextStep.complete("every objective mastered");
    }

    /** 以当前时间计算下一个目标。 */
    public static NextStep nextObjective(LearningProgress progress) {
        return nextObjective(progress, TimeSupport.nowSeconds());
    }

    /** 每个知识点的地图视图：状态 / 来源 / 掌握度 / 门槛 / 类型 / 接地。 */
    public static Map<String, Map<String, Object>> mapSummary(LearningProgress progress) {
        Map<String, Map<String, Object>> summary = new LinkedHashMap<>();
        for (LearningModule module : orderedModules(progress)) {
            for (KnowledgePoint kp : module.getKnowledgePoints()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", kp.getName());
                entry.put("module_id", module.getId());
                entry.put("module_name", module.getName());
                entry.put("type", kp.getType().value());
                entry.put("status", objectiveStatus(progress, kp));
                entry.put("source", masterySource(progress, kp));
                entry.put("mastery", displayMastery(progress, kp));
                entry.put("threshold", gateThreshold(kp.getType()));
                entry.put("grounding", kp.grounding());
                summary.put(kp.getId(), entry);
            }
        }
        return summary;
    }

    /** 模块按 {@code order} 升序；KP 保持列表顺序（不排序，与参考实现一致）。 */
    private static List<LearningModule> orderedModules(LearningProgress progress) {
        List<LearningModule> modules = new ArrayList<>(progress.getModules());
        modules.sort(Comparator.comparingInt(LearningModule::getOrder));
        return modules;
    }

    /** 供测试与调试：知识点 id → 掌握来源。 */
    static Map<String, String> masterySources(LearningProgress progress) {
        Map<String, String> sources = new HashMap<>();
        for (LearningModule module : orderedModules(progress)) {
            for (KnowledgePoint kp : module.getKnowledgePoints()) {
                sources.put(kp.getId(), masterySource(progress, kp));
            }
        }
        return sources;
    }
}
