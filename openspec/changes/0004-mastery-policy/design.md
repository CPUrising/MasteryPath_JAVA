# Design: 掌握门槛与前沿推进

## Context

参考实现 `learning/policy.py` 是一个**无状态纯函数模块**：所有判定都接收
`(LearningProgress, KnowledgePoint)` 并返回布尔/字符串。关键语义：

| 函数 | 语义 | 不变量 |
| --- | --- | --- |
| `is_assessed_mastered` | 先查附加证据，再按类型分支：定性类型看 `qualitative_mastery`，其余看 `mastery_levels >= 0.9` | INV-04/05/28 |
| `mastery_source` | `system`（评估通过）> `learner`（声明）> `""` | INV-01/03 |
| `is_mastered` | `mastery_source` 非空 | INV-03 |
| `eligible` | 所有前置都 `is_mastered` | INV-27 |
| `next_objective` | 待答题 > 第一个可选未掌握 > 到期复习 > complete | INV-02/03/27 |
| `objective_status` | mastered / learning / new | — |
| `require_type_unchanged` | 类型只读 | INV-06 |
| `map_summary` | 每个 KP 的状态/来源/分数/门槛/接地 | INV-03 |

`learning/mastery.py` 是另一个纯函数：近期加权正确率，衰减 0.6，跳过 `voided`。

## Goals / Non-Goals

**Goals**
1. 逐函数复刻语义，包括**判定顺序**（顺序影响结果，例如待答题优先级高于未掌握目标）。
2. TC-C1…C17、TC-F1/F6/F7/F8 全部有对应 Java 用例。
3. 纯函数层：零 I/O、零 Spring、无隐式时间（`now` 显式传入或默认取当前）。

**Non-Goals**
1. 不做缓存（每次调用重新计算；参考实现也如此，聚合规模很小）。
2. 不做门槛可配置化（`0.9` 是 ADR-007 锁定的常量）。
3. 不实现 `required_evidence` 的写入（属 `0008`），只实现其判定。

## Decisions

1. **`MasteryPolicy` 用静态方法，而非实例**
   - 选择：`public final class MasteryPolicy` + `static` 方法。
   - 理由：参考实现是模块级函数；无状态就不需要注入。避免调用方到处传实例。
   - 放弃的方案：Spring `@Service`（会让 `domain` 层依赖 Spring，违反分层约束）。

2. **门槛常量集中在一处**
   - 选择：`QUANTITATIVE_GATE = Map.of(MEMORY, 0.9, PROCEDURE, 0.9)`；
     `QUALITATIVE_TYPES = Set.of(CONCEPT, DESIGN)`。
   - 理由：`04-scheduling.md` §七 明确要求「门槛 0.9」与「desired_retention 0.9」是
     **两个独立常量**，不得共用一个。这里只定义前者。
   - 风险：若将来支持可配置，需改签名；当前按 ADR-007 固定。

3. **`NextStep` 用 record**
   - 选择：`record NextStep(String action, String moduleId, ...)` + `static` 工厂。
   - 理由：不可变的返回值；字段固定；`toMap()` 便于 REST 序列化（`0006` 起用）。
   - 放弃的方案：可变 POJO（无必要）。

4. **`nextObjective` 的判定顺序不可调整**
   - 顺序：`pendingQuestion` → 逐模块逐 KP（已掌握/已延后/不可选 均跳过）→ 到期复习 → `complete`。
   - 理由：`TC-C11`（待答题优先）与 `TC-C9`（第一个可选）依赖此顺序；
     调换会让「有待答题时仍返回 practice」这类错误静默通过。
   - 实现约束：模块按 `order` 升序，KP 按列表顺序（**不排序**，与参考实现一致）。

5. **`eligible` 对「前置不存在」的处理**
   - 参考实现：`prereq is None or not is_mastered(prereq)` → 不可选。
   - 选择：照抄。即**悬空前置视为未满足**（fail-closed）。
   - 理由：`0007` 的 build 校验会拒绝无法解析的前置，但历史数据可能有悬空引用；
     此时宁可让目标不可选，也不能放行。

6. **`dueReviews` 的排序**
   - 参考实现：`sort(key=lambda t: (-t.forgetting_risk, t.due_at))`。
   - 选择：`Comparator.comparingDouble(ReviewTask::getForgettingRisk).reversed()
     .thenComparingDouble(ReviewTask::getDueAt)`。
   - 理由：遗忘风险高的先复习；同风险按到期时间。

7. **`MasteryScore` 的衰减方向**
   - 参考实现：`rank = count - 1 - index`，`weight = 0.6^rank`。
     即**最新一条** `rank=0`（权重 1.0），最早一条权重最小。
   - 选择：照抄。这是「进步不被早期错误长期压制」的机制，方向反了会导致
     刚学会的人永远达不到 0.9。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 浮点比较 `>= 0.9` 的边界 | 0.9 恰好不通过 | 与参考实现一致（Python 也是 `>=`）；用 `0.9` 精确值测试（`TC-C2`） |
| 衰减方向写反 | 掌握度永远不达标 | `MasteryScoreTest` 用「早期错、近期对」的序列断言结果 > 0.9 |
| `nextObjective` 顺序被无意改动 | 语义漂移 | 用例分别覆盖四个分支，任一顺序错误都会挂 |
| 悬空前置放行 | 越过硬门控 | `eligible` 对缺失前置返回 false，并有专项用例 |

## Migration Plan

1. `MasteryScore`（无依赖，先写）。
2. `NextStep`（纯数据）。
3. `MasteryPolicy`（依赖前两者与模型）。
4. 测试：`MasteryPolicyTest`（TC-C1…C17、F1、F6–F8）+ `MasteryScoreTest`。
5. `verify.ps1 -Scope unit` → 全量 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/learning/policy.py` 与 `mastery.py` 为唯一依据。
