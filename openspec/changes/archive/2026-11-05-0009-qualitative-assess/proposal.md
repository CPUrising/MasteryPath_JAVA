# Proposal: 定性评估

## Why

`0008` 打开了定量类型（`memory` / `procedure`）的门槛，但 `concept` / `design`
**永远无法通过判分打开**——`grade` 刻意不写这两类的分数（`INV-05`）。它们只能走
Feynman 式的定性通道：学习者用自己的话解释，导师判定通过与否。

参考实现 `service.assess` 就是这个通道，并承担三件事：
1. 只允许 `concept` / `design`，对 `memory` / `procedure` 拒绝；
2. 写 `qualitativeMastery` 与 `QUALITATIVE` 类型证据；
3. 通过时清除门槛、发出 `objective.mastered`。

没有这一层，`concept` / `design` 类型的知识点在系统里是**死路**。

## What Changes

- 新增 `LearningService.assess`：定性评估入口（`passed` / `evidence` / `quality` / `review`）
- 新增 `AssessmentType.QUALITATIVE` 证据写入（枚举已在 `0002` 定义）
- 新增 `assess` 的题库写入（`question_type="written"`）

## Capabilities

### New Capabilities

无新增能力；实现 `specs/learning-tools` 的「Qualitative Assessment」需求的全部场景。

### Modified Capabilities

无。

## Impact

- 影响文件：`src/main/java/com/masterypath/service/LearningService.java`（补 1 个方法）、
  `scripts/verify.ps1`（补测试类过滤）。
- 影响测试：无既有测试被修改。
- 明确不做（Out of scope，登记到 `docs/verification-debt.md`）：
  - `review.scheduled` 事件与 SM-2 状态推进 → 属 `0010`。
  - `review` 模式下拒绝未掌握 KP 的**工具层**检查 → 属 `0013`；
    本 change 只在服务层提供 `review` 参数并写入 `REVIEW` 之外的证据类型（照抄参考实现，
    定性评估固定写 `QUALITATIVE`，不写 `REVIEW`）。
  - `mastery_assess` 工具本体与 mode 门控 → 属 `0013`。
