package com.masterypath.domain.policy;

import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningModule;
import com.masterypath.domain.model.LearningProgress;
import java.util.List;

/**
 * 测试工厂：构造路径聚合（对应参考实现的 {@code tests/factories.py}）。
 *
 * <p>注意 {@code kp()} 默认带一个来源（{@code s1}），使知识点默认「接地」；
 * 需要未接地时显式传空列表。
 */
final class PolicyFixtures {

    private PolicyFixtures() {
    }

    static KnowledgePoint kp(String id, String name, KnowledgeType type) {
        return kp(id, name, type, "m1", List.of(), List.of("s1"));
    }

    static KnowledgePoint kp(String id, String name, KnowledgeType type,
                             String moduleId, List<String> prereq, List<String> sources) {
        KnowledgePoint point = new KnowledgePoint(id, name, type, moduleId);
        point.setPrerequisiteIds(prereq);
        point.setSourceIds(sources);
        return point;
    }

    static LearningModule module(String id, String name, int order, List<KnowledgePoint> kps) {
        return module(id, name, order, kps, "", 0.7);
    }

    static LearningModule module(String id, String name, int order,
                                 List<KnowledgePoint> kps, String objective, double displayTarget) {
        LearningModule module = new LearningModule(id, name, order);
        module.setObjective(objective);
        module.setDisplayTarget(displayTarget);
        module.setKnowledgePoints(kps);
        return module;
    }

    static LearningProgress progress(List<LearningModule> modules) {
        LearningProgress progress = new LearningProgress("p1");
        progress.setModules(modules);
        return progress;
    }
}
