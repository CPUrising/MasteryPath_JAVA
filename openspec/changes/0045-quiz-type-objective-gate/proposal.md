# Proposal: 定量目标的题型门控

## Why

`memory` / `procedure` 目标走 0.9 的定量门槛，但 `open` 题判对在服务层被强制降为
`partial`（INV-35），而 `MasteryScore` 只把 `correct` 计为 1，`partial` 计为 0 且仍占用
最近 5 次窗口。于是出现一个不可赢的局面：学习者把 open 题答得完全正确，掌握度不升反降。

真实数据（`path=pmw8vlwsu`，知识点 `k5`，`procedure`）：连续两条 open 题的正确答案被记为
`is_correct=false`，掌握度从 `0.6607` 掉到 `0.4714`。同一路径下 `mastery_quiz` 仍在为
`procedure` 目标出 open 题。

此外，`mastery_repair_question(action=correct)` 只替换 `expected_answer`，不更新注册时固定的
`required_keywords` / `optional_keywords`，且 open 题仍被降为 `partial`——所以修复工具对 open
题实际无法恢复任何掌握度，但返回文案仍让学习者以为记录已被修正。

## What Changes

- `mastery_quiz` 注册期校验：目标门槛为 `quantitative`（`memory` / `procedure`）时，MUST 拒绝
  `question_type=open`；拒绝文案指向 `choice`、不超过 30 字的 `short`（可配 `accepted_answers`），
  以及 `concept` / `design` 才走 `mastery_assess`。
- `mastery_quiz` 工具描述补充题型约束，明确 open 判对也只记 `partial`、不产生掌握度。
- `mastery_repair_question(action=correct)` 对 open 题返回 `effective=false` 与可读说明；
  工具文案 MUST NOT 再暗示掌握度已恢复。
- 不修改 `Grading`、`MasteryScore`、权重与置信上限，也不修改 `INV-35`。
- 运维数据修复：作废本次误配置产生的 3 条 open 记录，并以 `choice` / `short` 重新出题。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `learning-tools`：新增「题型必须匹配目标门槛」的注册约束；明确修复返回体的
  `effective` 语义。

## Impact

- Java：`StudyTools`（注册校验、工具描述）、`LearningService`（修复返回体）、
  `RepairTools`（修复文案）、`ToolSchemas`（`question_type` 参数说明）。
- 测试：`StatusAndQuizToolsTest`、`RepairQuestionTest`。
- 行为：定量目标不再接受 open 题；历史 open 题仍可作废/改键，但 repair 不再暗示涨分。
- 数据：`data/learning.db` 中 3 条 open 记录作废（运维操作，留痕于验证文档）。
