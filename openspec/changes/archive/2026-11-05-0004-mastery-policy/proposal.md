# Proposal: 掌握门槛与前沿推进

## Why

`0002` 定义了实体，`0003` 让它们能持久化，但系统还不知道「什么叫掌握」。
参考实现把这件事收敛成一个纯函数层 `learning/policy.py`（240 行）：
**门槛判定 + 状态派生 + 下一个目标**。它是整个产品的核心语义——
导师是否推进、UI 进度环显示什么、复习队列是否排期，全都读这一个结论。

如果这层不等价，后续 `0007`（大纲）、`0008`（判分）、`0010`（调度）全部会建立错的地基。

## What Changes

- 新增 `MasteryPolicy`：门槛判定与状态派生（纯函数，零 I/O）
  - `isAssessedMastered` / `isMastered` / `masterySource`
  - `objectiveStatus` / `mapSummary` / `displayMastery`
  - `eligible`（前置硬门控）/ `nextObjective`（唯一推进依据）
  - `activeAttempts` / `achievedEvidence` / `evidenceSatisfied`
  - `requireTypeUnchanged`（类型只读）
  - `dueReviews`（复习到期筛选，供 `0010` 复用）
- 新增 `MasteryScore`：近期加权正确率（衰减系数 0.6），供判分写入 `mastery_levels`
- 新增 `NextStep`：下一个目标的返回值（`action` + 定位信息 + 门槛快照）

## Capabilities

### New Capabilities

无新增能力；本变更实现 `specs/learning-domain` 中的
「Mastery Gate」「Frontier Advancement」「Goal Preservation」三条需求。

### Modified Capabilities

无。

## Impact

- 影响文件：`src/main/java/com/masterypath/domain/policy/**`（新增 3 个类）、
  `src/test/java/com/masterypath/domain/policy/**`（新增 2 个测试类）。
- 影响测试：无既有测试被修改。
- 明确不做（Out of scope）：
  - 不实现 `build`/`revise` 的地图校验（属 `0007`）。
  - 不实现判分（属 `0008`）——本 change 只提供 `MasteryScore` 供其调用。
  - 不实现 SM-2 排期（属 `0010`）——本 change 只提供 `dueReviews` 的筛选逻辑。
  - 不实现 `goal` 保留的写入逻辑（属 `0006` 服务层）；本 change 只用测试证明聚合层不会被清空。
