package com.masterypath.service;

import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningModule;
import com.masterypath.domain.model.LearningProgress;

import java.util.List;

/**
 * 服务层测试工厂：构造已建好的路径聚合。
 *
 * <p>与 {@code domain/policy/PolicyFixtures} 分开，是因为服务层测试还需要直接把聚合写进
 * 真实 SQLite（走 {@code LearningStore.create}），而不只是内存对象。
 */
final class ServiceFixtures {

    private ServiceFixtures() {
    }

    static KnowledgePoint kp(String id, String name, KnowledgeType type) {
        KnowledgePoint point = new KnowledgePoint(id, name, type, "m1");
        point.setSourceIds(List.of("s1"));
        return point;
    }

    static LearningModule module(String id, String name, int order, List<KnowledgePoint> kps) {
        LearningModule module = new LearningModule(id, name, order);
        module.setKnowledgePoints(kps);
        return module;
    }

    /** 单模块单知识点的路径。 */
    static LearningProgress onePoint(String pathId, KnowledgeType type) {
        LearningProgress progress = new LearningProgress(pathId);
        progress.setName("Path");
        progress.setGoal("learn X");
        progress.setModules(List.of(module("m1", "M1", 0,
                List.of(kp("k1", "fact one", type)))));
        return progress;
    }
}
