# Proposal: 出题与判分

## Why

`0007` 让路径有了知识点，但无法提问与判定——学习者答对了也不知道是否掌握。
参考实现把这件事拆成两层：

- `learning/grading.py`（162 行**纯函数**）：确定性判分，**不调用 LLM**；
- `service.register_question` / `grade`：单槽位、幂等、证据追加、掌握度重算。

核心不变量：**无法解析的作答永不判错**，而是返回 `ungraded` 且零状态变更（`INV-46`）。

没有这一层，`0009`（定性评估）与 `0010`（复习调度）都没有数据来源。

## What Changes

- 新增 `Grading`（`domain/grading`）：`normalize` / `similarity` / `resolveChoice` /
  `grade(question, answer)` → `Decision`，覆盖 choice / short / open 三类
- 新增 `QuizSpec`（`domain/quiz`）：出题参数校验（选项标签连续、选项正文唯一、
  期望答案可解析为标签、简答禁带选项）
- 新增 `LearningService.registerQuestion` / `grade` / `skipQuestion`
- 新增 `LearningService.listQuestions` / `tagQuestion`（题库读写在 `0003` 已具备）

## Capabilities

### New Capabilities

无新增能力；实现 `specs/learning-tools` 的「Single Question Slot」与
「Deterministic Grading」两条需求的全部场景。

### Modified Capabilities

无。

## Impact

- 影响文件：`src/main/java/com/masterypath/domain/grading/**`（新增 3 个类）、
  `src/main/java/com/masterypath/domain/quiz/**`（新增 2 个类）、
  `src/main/java/com/masterypath/service/LearningService.java`（补 4 个方法）、
  `scripts/verify.ps1`（补测试类过滤）。
- 影响测试：无既有测试被修改。
- 明确不做（Out of scope，登记到 `docs/verification-debt.md`）：
  - `review.scheduled` 事件与 SM-2 状态推进 → 属 `0010`。本 change 只计算
    `gateCleared` 并返回，不播种复习计划。
  - `assess`（定性评估）→ 属 `0009`。
  - `repairQuestion`（void / correct）→ 属 `0012`。
  - 工具层（`mastery_quiz` / `mastery_grade` 的 mode 门控与 `ToolResult` 包装）→ 属 `0013`；
    本 change 只实现服务层与纯函数，模式许可已由 `0005` 的 `ModeGate` 提供。
