# Design: 服务核心

## Context

参考实现 `learning/service.py` 的 `LearningService` 是**唯一写聚合的入口**：每个方法都包成
`store.mutate(path_id, fn)`，在回调里改 `tx.progress` 并 `tx.emit(...)`。它自己不实现业务判定，
判定一律委托给 `learning/policy.py`（已由 `0004` 复刻）。

本 change 只取其中**不依赖判分、大纲、调度**的那一批方法。

## Goals / Non-Goals

**Goals**

1. 逐方法复刻：错误文案、事件名与 payload、返回值形状。
2. 学习者声明掌握必须**不写分数、不产证据**（`INV-01`/`INV-15`），且 `gate_cleared` 语义正确。
3. 学习者可见投影只含 7 个白名单字段（`INV-07`）。
4. `deletePath` 的级联范围与参考实现一致，且**不删 events**。

**Non-Goals**

1. 不实现 `review.scheduled` 事件与学习者首间隔（`0010`）。
2. 不实现 `build` / `revise` / `registerQuestion` / `grade` / `assess`。
3. 不实现 `getState` 的完整快照形状（`0014` 需要时才定形）。
4. 不做空值校验「加固」：参考实现不校验 `name` 为空、不校验 `goal` 为空，这里照抄。

## Decisions

1. **`LearningService` 是普通类，不是 Spring `@Service`**
   - 选择：`public final class LearningService` + 构造注入 `LearningStore`。
   - 理由：`0001` 的 `AppContextTest` 只验证上下文能装配；此时加 Spring 注解会让
     `service` 层在无容器时不可测。装配留到 `0014`（REST 需要 Bean 时）。
   - 影响：测试直接 `new LearningService(store)`，与参考实现 `LearningService(store)` 一致。

2. **`createPath` 用 `store.load` 前置检查 + `mutate(createIfMissing=true)`**
   - 参考实现同样在事务外检查（存在 TOCTOU 窗口），照抄，不额外加锁。
   - 错误文案逐字：`"path already exists: " + pathId`。
   - 返回值是 `LearningProgress`（不是 `Map`），与参考实现一致。

3. **`overrideMastery` 的 `gate_cleared` 在写入 override 之后计算**
   - 顺序（顺序即语义）：
     1. `wasMastered = MasteryPolicy.isMastered(progress, kp)`
     2. 写入 `learnerMasteryOverrides[kpId]`
     3. `gateCleared = MasteryPolicy.isMastered(progress, kp) && !wasMastered`
   - 理由：`isMastered` 读 override，所以必须先写再算；若先算后写，`gateCleared` 永远为 false。
   - 已知缺口：参考实现在 `gateCleared` 时调用 `_schedule_after(...)` 播种复习计划并发
     `review.scheduled`。该能力属 `0010`，本 change **不实现**，只返回 `gateCleared` 布尔值。
     已登记到 `docs/verification-debt.md`。

4. **`deferObjective` 的 payload 不含 `note`**
   - 参考实现：`tx.emit("objective.deferred", {"knowledge_point_id": kp_id})`，`note` 只落在聚合。
   - 而 `noteExplained` 的 payload **含** `summary`。
   - 理由：两者不对称是参考实现的原样行为，不做「统一」——事件是外部契约，改了就破坏兼容。

5. **两套错误文案并存，不统一**
   - `overrideMastery`：`"unknown knowledge point: " + kpId`
   - `deferObjective` / `noteExplained`：`"unknown objective: " + kpId`
   - 理由：参考实现就是两套。合并会让已有调用方的错误匹配失效。

6. **`QuestionCardProjector` 用白名单，不用黑名单**
   - 选择：显式构造 7 键 `LinkedHashMap`：`question_id` / `knowledge_point_id` /
     `module_id` / `prompt` / `question_type` / `options` / `difficulty`。
   - 理由：黑名单（排除 `expected_answer` / `explanation`）在新增敏感字段时会静默泄漏；
     白名单在新增字段时只会「不展示」，是 fail-closed。
   - 放在 `domain/projection` 而非 `service`：它是纯函数，无 I/O，可单测。

7. **`deletePath` 放进 `LearningStore`，不放进 `LearningService`**
   - 参考实现是 `store.delete_path`，`service.delete` 只是转发。
   - 理由：级联删除需要单事务 + 连接，属存储职责；`service` 层没有 `Transaction` 句柄可用
     （`mutate` 只能改聚合，不能删行）。
   - 顺序固定：messages → sessions → question_bank → interactions → paths。
   - **不删 events**：事件表 append-only，删路径后历史事件仍可读（参考实现如此）。

8. **`attachSources` 与 `setKnowledgeBaseSources` 的差异必须保留**
   - `attachSources`：`sources = 新列表`（整体替换），事件 `sources.attached`。
   - `setKnowledgeBaseSources`：`sources = 非KB项 + 新KB列表`（只替换 KB 类），事件 `sources.knowledge_bases`。
   - 理由：UI 添加/移除知识库时不能清掉用户已附加的文件来源。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| `gateCleared` 先算后写 | 永远返回 `false`，声明掌握不触发任何后续 | 用例断言「未掌握 → 声明 → `gate_cleared=true`」，且断言顺序敏感 |
| 投影写成黑名单 | 未来新增 `rubric` 等字段会泄漏 | 白名单 + 用例断言 key 集合**恰好**是 7 个 |
| 把 `deferred` 的 payload 补上 `note` | 破坏事件契约 | 用例逐字断言 payload 只有 1 个键 |
| `deletePath` 删了 events | 破坏审计日志与 `events_since` | 用例断言删除后 `eventsSince` 仍能读到历史事件 |
| 把 `override` 写成「同时写 `masteryLevels`」 | 违反 `INV-01`，声明变成作弊入口 | 用例断言 `masteryLevels` 与 `learningEvidence` 均无变化 |

## Migration Plan

1. `QuestionCardProjector`（纯函数，无依赖）。
2. `LearningStore.deletePath`（存储层，独立）。
3. `LearningService`（依赖前两者 + `MasteryPolicy`）。
4. 测试：`QuestionCardProjectorTest`、`PathLifecycleTest`、`SourceAttachmentTest`、
   `MasteryOverrideServiceTest`、`ObjectiveStateTest`、`PathDeletionTest`。
5. `verify.ps1 -Scope unit` → 全量 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/learning/service.py` 与 `pending.py` 为唯一依据。
- 已知参考实现的测试空白（本 change 会补上，并登记为「Java 侧超出参考测试覆盖」）：
  - `defer_objective` / `note_explained` 在服务层无直接测试；
  - 「`mastery.override` 事件存在但 `learning_evidence` 无新增」这一组合断言在 `tests/` 中不存在；
  - `delete_path` 的级联范围无直接断言。
