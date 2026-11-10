# Proposal: 回合证据回放

## Why

真实对话里出现「学习者明明在卡片上作答，导师却说卡片没送到，并反复重新出题」。

排查 `data/learning.db` 的真实会话（`3d0m8lwouqy`）后确认判分本身是成功的：

- 15:21:59 出题 `85039278…`（`question.registered` + `data.card`）
- 15:22:19 `attempt.graded` → `is_correct=true`、`mastery_after=0.4625`
- 15:22:28 同一回合落库 `user: "C"` 与 `assistant: "上一题的卡片没送出去…"` + 新卡

根因在上下文装配而不是判分：

1. `TurnRunner.historyMessages()` 只回放消息的 `role` + `content`，而题卡与判分只存在于 `data.card` /
   `data.grade`。学习者提交作答时前端把答案当普通消息发出（`message = "C"`），于是模型看到的
   历史是「assistant 说『下面这题…』但没有题」+「user 说 C」——它只能推断卡片没送到。
2. WS 回合从不把判分写进消息 `data`（0040 登记的偏差 D1），因此即使回放也没有判分可读。
3. `events` 表所有行的 `session_id` / `turn_id` 都是空串，无法把事件归因到会话与回合。
4. 判分后 `pending_question` 立即清空，`mastery_status` 快照里没有任何「刚答过哪题、结果如何」的痕迹。

## What Changes

- `historyMessages()` 把 `data.card`（题号、题型、题干、选项）与 `data.grade`（作答、结果、判分后掌握度）
  还原成文本证据，附在 assistant 历史消息里。
- WS 回合落库时把判分结果写入 assistant 消息的 `data.grade`（`recordTurn` 新增 `grade` 参数）。
- 回合内产生的领域事件（`question.registered`、`attempt.graded`、`objective.mastered`、
  `qualitative.assessed`、`question.skipped`、`objective.explained`）携带 `session_id` / `turn_id`。
- 路径状态快照新增 `last_answered`（题号、作答原文、结果、提示数、判分后掌握度、会话/回合），
  从未作答时为 `{status: "none"}`；REST 与 `mastery_status` 工具都暴露。

## Capabilities

### Modified Capabilities

- `agent-turn`：会话历史回放题卡与判分；WS 回合把判分写入 `data.grade`。
- `learning-storage`：回合内事件携带 `session_id` / `turn_id`。
- `learning-tools`：`mastery_status` 增加 `last_answered`。

## Impact

- Java：`TurnRunner`、`LearningService`（`recordTurn` / `getState` / 事件发射）、`StudyTools`、
  `StatePayload`。
- 测试：`TurnRunnerTest`、`PathStateTest`、`ObjectiveStateTest`、`GradeFlowTest`、
  `QuizRegistrationTest`。
- 行为：模型能看到自己发过的卡片与判分；`session_id` / `turn_id` 不再为空；状态快照多一个键
  （learner 可见，仅含其本人作答，不含 `expected_answer`）。
