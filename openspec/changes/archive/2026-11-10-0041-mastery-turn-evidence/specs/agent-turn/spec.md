# agent-turn Specification

## MODIFIED Requirements

### Requirement: Mastery Answer And Skip Turns

WS 回合 SHALL 接受 `mastery_answer` 与 `mastery_skip`。二者存在时，系统 MUST 在启动导师
LLM 之前完成判分或跳过，并以 `tool_result` 事件发出结果（metadata 键分别为
`mastery_grade` / `mastery_skip`）。作答文本 MUST 作为本轮用户消息；导师随后 SHALL 正常流式回复。
回合 MUST 携带此前的会话历史（role + content），且 assistant 历史消息 MUST NOT 只含正文：
消息 `data.card` 与 `data.grade` SHALL 被还原成文本证据一并回放。

#### Scenario: Answer is graded before the tutor speaks

- **GIVEN** 一道待答题目与学习者提交的 `mastery_answer`
- **WHEN** 发起回合
- **THEN** 系统 MUST 先发 `tool_result`（含 `mastery_grade`），再发导师的 `text_delta`
- **AND** 判分 MUST NOT 依赖前端单独调用 REST

#### Scenario: Answer text is the user message

- **GIVEN** `mastery_answer.text` 为 `B` 且 `message` 为空
- **WHEN** 发起回合
- **THEN** LLM 收到的用户消息 MUST 为 `B`

#### Scenario: Skip clears the card before the tutor speaks

- **GIVEN** 一道待答题目与 `mastery_skip`
- **WHEN** 发起回合
- **THEN** 系统 MUST 先清空该待答题并发 `tool_result`（含 `mastery_skip`）
- **AND** 导师的 `mastery_status` 快照 MUST 显示无待答题

#### Scenario: History is included

- **GIVEN** 会话中已有若干讲师与学习者消息
- **WHEN** 新回合开始
- **THEN** 发给 LLM 的消息 MUST 含这些历史（跳过空正文）

#### Scenario: History replays delivered cards and grades

- **GIVEN** 会话中一条 assistant 消息带 `data.card`、另一条带 `data.grade`
- **WHEN** 新回合开始
- **THEN** 发给 LLM 的历史 MUST 含该卡片的 `question_id`、题干与选项
- **AND** MUST 含该次判分的 `learner_answer` 与 `result`
- **AND** 该证据 MUST NOT 含 `expected_answer` 或 `explanation`

#### Scenario: Graded card is persisted with its outcome

- **GIVEN** 一个携带 `mastery_answer` 并已完成判分的 WS 回合
- **WHEN** 回合落库
- **THEN** 该回合 assistant 消息的 `data.grade` MUST 含 `question_id` 与 `result`
- **AND** 同一消息 MAY 同时含 `data.card`
