package com.masterypath.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识点（spec 01 §二）。
 *
 * <p>校验：{@code name} 去空白后 MUST 至少 2 字符（{@code TC-A1}）。校验写在构造器与 setter 内，
 * 因此「直接构造」与「JSON 反序列化」两条路径都会拒绝非法值——这是 Pydantic 的行为，
 * 仅靠 Bean Validation 注解无法覆盖反序列化路径。
 *
 * <p>{@code grounding} 是派生值（{@code source_ids} 是否非空），与参考实现一致，
 * <b>不参与序列化</b>（Python 侧是 {@code @property}，不进 {@code model_dump}）。
 */
public class KnowledgePoint {

    private String id;
    private String name;
    private KnowledgeType type;
    private String moduleId;
    private List<String> prerequisiteIds = new ArrayList<>();
    private List<String> sourceIds = new ArrayList<>();
    /** 附加必需证据类型（{@code INV-28}）；为空表示无额外条件。 */
    private List<String> requiredEvidence = new ArrayList<>();

    public KnowledgePoint() {
    }

    public KnowledgePoint(String id, String name, KnowledgeType type, String moduleId) {
        this.id = id;
        setName(name);
        this.type = type;
        this.moduleId = moduleId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.trim().length() < 2) {
            throw new IllegalArgumentException("knowledge point name must be at least 2 characters");
        }
        this.name = name;
    }

    public KnowledgeType getType() {
        return type;
    }

    public void setType(KnowledgeType type) {
        this.type = type;
    }

    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public List<String> getPrerequisiteIds() {
        return prerequisiteIds;
    }

    public void setPrerequisiteIds(List<String> prerequisiteIds) {
        this.prerequisiteIds = prerequisiteIds == null ? new ArrayList<>() : prerequisiteIds;
    }

    public List<String> getSourceIds() {
        return sourceIds;
    }

    public void setSourceIds(List<String> sourceIds) {
        this.sourceIds = sourceIds == null ? new ArrayList<>() : sourceIds;
    }

    public List<String> getRequiredEvidence() {
        return requiredEvidence;
    }

    public void setRequiredEvidence(List<String> requiredEvidence) {
        this.requiredEvidence = requiredEvidence == null ? new ArrayList<>() : requiredEvidence;
    }

    /** 派生：有来源即接地（{@code INV-24}）。刻意不用 getter 命名，避免被序列化。 */
    public String grounding() {
        return sourceIds == null || sourceIds.isEmpty() ? "ungrounded" : "grounded";
    }
}
