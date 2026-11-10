# agent-turn Specification

## MODIFIED Requirements

### Requirement: Mastery Answer And Skip Turns

WS 回合 SHALL 接受 `mastery_answer` 与 `mastery_skip`。二者存在时，系统 MUST 在启动导师
LLM 之前完成判分或跳过，并以 `tool_result` 事件发出结果（metadata 键分别为
`mastery_grade` / `mastery_skip`）。作答文本 MUST 作为本轮用户消息；导师随后 SHALL 正常流式回复。
回合 MUST 携带此前的会话历史（role + content），且历史正文 MUST 只含真实对话：
题卡与判分证据 SHALL 通过运行时上下文注入，MUST NOT 拼进 assistant 的 `content`。

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
- **THEN** 运行时上下文 MUST 含 `[Session recap` 段，列出该卡片的题干与选项
- **AND** MUST 含该次判分的 `learner_answer` 与 `result`
- **AND** 该投影 MUST NOT 含 `expected_answer`
- **AND** 发给 LLM 的 assistant 历史正文 MUST NOT 含该段文本或题号

#### Scenario: Runtime evidence never enters assistant text

- **GIVEN** 会话中一条 assistant 消息带 `data.card`、另一条带 `data.grade`
- **WHEN** 新回合开始
- **THEN** 发给 LLM 的历史正文 MUST NOT 含题号、`[Question card delivered` 或
  `[That card was graded]`
- **AND** 运行时上下文 MUST 含 `[Session recap` 段，列出这些卡片与判分
- **AND** 该段 MUST 声明其为运行时数据、不得复述

#### Scenario: Graded card is persisted with its outcome

- **GIVEN** 一个携带 `mastery_answer` 并已完成判分的 WS 回合
- **WHEN** 回合落库
- **THEN** 该回合 assistant 消息的 `data.grade` MUST 含 `question_id` 与 `result`
- **AND** 同一消息 MAY 同时含 `data.card`

## ADDED Requirements

### Requirement: Session Continuity And Answer Entry

掌握之路会话页 SHALL 在地址不含 `session` 查询参数时自动接上该路径最近一次会话，并把
`session_id` 写回地址栏；刷新或重新进入 MUST 按消息顺序完整渲染全部历史，MUST NOT 丢弃
带 `data.card` / `data.grade` 的消息正文，MUST NOT 做条数截断。
存在 `pending_question` 时，底部输入框提交的内容 SHALL 作为该题的作答（`mastery_answer`）；
题卡 MUST NOT 再提供独立的自由文本输入框。

#### Scenario: Reopening resumes the latest session

- **GIVEN** 路径下已有若干会话，且地址不含 `session` 参数
- **WHEN** 打开会话页
- **THEN** 前端 MUST 加载 `updated_at` 最新的那个会话
- **AND** MUST 把该 `session_id` 写入地址栏

#### Scenario: Full history is rendered after reload

- **GIVEN** 一个含用户消息、讲师正文、题卡与判分的会话
- **WHEN** 刷新页面
- **THEN** 消息流 MUST 含全部消息，且每条消息的正文与其题卡/判分 MUST 分别显示
- **AND** MUST NOT 只显示最近的待答题

#### Scenario: Composer answers the open question

- **GIVEN** 会话中存在 `pending_question` 且题卡无选项
- **WHEN** 学习者在底部输入框发送文本
- **THEN** 前端 MUST 以 `mastery_answer.question_id = pending_question.question_id` 发起回合
- **AND** 题卡内 MUST NOT 出现第二个输入框

#### Scenario: Session list opens a specific session

- **GIVEN** 右侧会话列表有多条记录
- **WHEN** 学习者点某条的「打开」
- **THEN** 跳转地址 MUST 带该条的 `session_id`
