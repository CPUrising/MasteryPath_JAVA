# AI 协作留痕：0006-service-core

## 1. prompt 范围

- 授权修改的文件：`src/main/java/com/masterypath/service/LearningService.java`（新增）、
  `src/main/java/com/masterypath/domain/projection/**`（新增 2 个文件）、
  `src/main/java/com/masterypath/storage/LearningStore.java`（补 `deletePath`）、
  `src/test/java/com/masterypath/service/**`（5 个测试类 + 1 个工厂）、
  `src/test/java/com/masterypath/domain/projection/**`（1 个测试类）、
  `src/test/java/com/masterypath/storage/PathDeletionTest.java`、
  `scripts/verify.ps1`（补测试类过滤）、`openspec/changes/0006-service-core/**`、`docs/**`。
- 明确不做的事：不实现 build/revise（`0007`）、出题判分（`0008`）、SM-2 调度（`0010`）、
  REST（`0014`）、导航与画像（`0012`）。
- 出处：用户指令原文「需要 `git 提交` 。下一步按计划是 `0006-service-cor`」；
  `docs/migration-plan.md` §5 第 6 行。

## 2. AI 产出

- 新增文件：
  - `LearningService`（路径生命周期、来源附加、声明掌握、defer、已讲解）
  - `QuestionCardProjector`（学习者可见投影，7 个白名单字段）
  - `LearningStore.deletePath`（级联删除，events 保留）
  - 测试 6 个：`PathLifecycleTest`（13）、`SourceAttachmentTest`（8）、
    `MasteryOverrideServiceTest`（15）、`ObjectiveStateTest`（12）、
    `PathDeletionTest`（8）、`QuestionCardProjectorTest`（7）
- 修改文件：`scripts/verify.ps1`（`-Scope unit` 补 `*ProjectorTest,*LifecycleTest,*AttachmentTest,
  *OverrideServiceTest,*ObjectiveStateTest`；`-Scope storage` 补 `*DeletionTest`）
- 关键决策：
  - `LearningService` 用普通类而非 Spring `@Service`：此时加注解会让 service 层在无容器时
    不可测；装配留到 `0014`。
  - `gateCleared` 必须在**写入 override 之后**计算：`isMastered` 读的就是 override，
    先算后写会恒为 `false`。
  - 两套错误文案并存（`unknown knowledge point:` vs `unknown objective:`）与两套事件 payload
    不对称（defer 不含 note、explained 含 summary）——**均为参考实现原样行为，不统一**。
  - 投影用**白名单**而非黑名单：黑名单在新增敏感字段时会静默泄漏。
  - `deletePath` 放进 `LearningStore` 而非 `LearningService`：级联删除需要单事务与连接，
    `mutate` 只能改聚合、不能删行。
  - `deletePath` **不删 events**：事件表 append-only，删路径后历史事件仍可读。

## 3. 人工修正点

- 无人类介入（人类尚未审查本 change 产出）。
- AI 自我修正（提交前完成）：
  1. `containsExactlyInAnyOrderKeys` 不存在于 AssertJ `MapAssert` → 改用 `containsOnlyKeys`。
  2. `verify.ps1 -Scope unit` 漏掉 `QuestionCardProjectorTest`（类名不含 `Projection`）→ 补 `*ProjectorTest`。
  3. `PathDeletionTest` 初稿调用了不存在的 `MasteryInteraction#setKnowledgePointId` → 删除该行。
  4. 更新 `traceability.md` 时一次替换破坏了 TC-K2 行的表格结构 → 立即修正并复核。

## 4. 回归结果

- 用例数 / 失败数：本 change 新增 63 / 0；全量 193 / 0。
- 证据链接：[`docs/verify/0006-service-core.md`](../verify/0006-service-core.md)
- 日志：`artifacts/verify-0006-service-core-20261104-134002.log`
- 参考实现对照：`pytest tests/test_policy.py tests/test_interactions.py tests/test_path_events.py
  tests/test_preferences.py -q` → **29 passed**。

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| `gateCleared` 计算时机 | **写入 override 之后** | `isMastered` 读 override；先算后写恒为 false |
| 声明掌握是否写分数 | **不写** | `INV-01`；用例断言分数保持 0.1 |
| 声明掌握是否产证据 | **不产** | `INV-15`；用例断言 `learningEvidence` 为空 |
| 两套错误文案是否统一 | **不统一** | 参考实现就是两套，改文案会破坏已有调用方的错误匹配 |
| `objective.deferred` 是否带 note | **不带** | 参考实现原样；用例逐字断言 payload 只有 1 个键 |
| `objective.explained` 是否带 summary | **带** | 与 defer 不对称，属参考实现行为 |
| 投影用白名单还是黑名单 | **白名单** | 黑名单在新增敏感字段时静默泄漏；白名单 fail-closed |
| 投影字段数 | **恰好 7 个** | 与 `public_pending_question` 一致；用例用 `containsOnlyKeys` 锁定 |
| `deletePath` 是否删 events | **不删** | 事件表 append-only（`INV-38`）；用例断言删除后事件仍可读 |
| `deletePath` 归属哪一层 | **存储层** | 级联删除需单事务 + 连接；`mutate` 只能改聚合 |
| `createPath` 是否加锁防 TOCTOU | **不加** | 照抄参考实现，不扩大范围 |
| `rename` 是否校验空名 | **不校验** | 参考实现不校验；加校验即行为变化 |
| 是否实现 `review.scheduled` | **不实现** | 属 `0010`；已登记验证债，只返回 `gateCleared` 布尔值 |
