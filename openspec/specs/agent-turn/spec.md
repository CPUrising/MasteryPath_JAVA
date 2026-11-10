# Agent Turn Capability Spec

## Purpose

定义一轮导师对话的编排契约：WS 回合的请求与事件、REST 端点、领域事件命名、错误码，以及 LLM 适配边界。前端唯一依赖即这些契约，因此它们必须在实现前冻结。

## Requirements

### Requirement: Transport Split

系统 SHALL 让「一轮导师对话」只走 WebSocket，其余所有操作走 REST。WS 请求 MUST 携带会话、路径、模式与回合标识；事件 MUST 为 `text_delta` / `tool_call` / `tool_result` / `question_card` / `session_meta` / `turn_end` / `error`。

#### Scenario: TC-I1 REST mode gate

- **GIVEN** 当前会话模式不是 `outline`
- **WHEN** 通过 REST 写地图
- **THEN** 系统 MUST 返回模式冲突错误且地图 MUST 不变

#### Scenario: TC-I2 rebuild requires confirm over REST

- **GIVEN** 非空路径
- **WHEN** 调用重建端点且未带 `confirm`
- **THEN** 系统 MUST 拒绝

#### Scenario: TC-I3 projection hides answers

- **GIVEN** 一张待答题目卡
- **WHEN** 序列化为学习者可见投影
- **THEN** 投影 MUST NOT 含期望答案与解析

#### Scenario: TC-I7 override emits event without evidence

- **GIVEN** 一次学习者声明掌握
- **WHEN** 提交
- **THEN** 系统 MUST 发出声明事件且 `learning_evidence` MUST 无新增

### Requirement: Turn Orchestration

系统 SHALL 在回合开始前一次性装配工具 schema 与系统提示，并在调用期做模式校验。回合内 MUST 支持流式文本增量、工具调用与结果透传；工具结果中的卡片元数据 MUST 原样透出给前端。

#### Scenario: WS event order

- **GIVEN** 一轮会触发工具调用的导师对话
- **WHEN** 消费 WS 事件流
- **THEN** 顺序 MUST 为 `tool_call` → `tool_result` → `text_delta` → `turn_end`

#### Scenario: Partial text survives stream failure

- **GIVEN** 模型流在中途失败
- **WHEN** 回合结束
- **THEN** 已产出的文本 MUST 被保留，且 MUST 发出 `error` 事件

### Requirement: Provider Isolation

系统 SHALL 把供应商差异收敛在适配层：领域层 MUST NOT 依赖任何供应商类型，适配层 MUST 提供可离线运行的假客户端。

#### Scenario: Offline test client

- **GIVEN** 无网络环境
- **WHEN** 运行回合相关单元测试
- **THEN** 测试 MUST 通过，且 MUST NOT 发起真实网络请求

### Requirement: Question Card Event Contract

系统 SHALL 在 `question_card` 事件中携带学习者可见题卡，并附加 `card_kind`、`turn_id` 与
`objective {id, name}`；事件 MUST NOT 含 `expected_answer`、`explanation`、`accepted_answers`、
`required_keywords`、`optional_keywords`。既有 7 个公开键 MUST 保持不变。

#### Scenario: Card event carries turn context

- **GIVEN** 一轮 `study` 对话通过 `mastery_quiz` 注册了题目
- **WHEN** 消费方读取 `question_card` 事件
- **THEN** 事件 MUST 含 `card_kind`、`turn_id`、`objective.id`、`objective.name` 与既有 7 个公开键

#### Scenario: TC-I3 event projection hides answers

- **GIVEN** 一张待答题目卡
- **WHEN** 序列化为 `question_card` 事件
- **THEN** 事件 MUST NOT 含期望答案、解析或任何关键词字段

### Requirement: Question Card History

系统 SHALL 把回合产出的题卡写入 assistant 消息的 `data.card`；会话详情 MUST 原样返回该投影，
使前端刷新后无需依赖 `pending_question` 也能恢复题卡。同一 `question_id` MUST NOT 在同一会话中
重复出现为多个待答卡。

#### Scenario: Reload restores card from history

- **GIVEN** 一个已产生题卡并已落库的 mastery 会话
- **WHEN** 重新读取会话详情
- **THEN** assistant 消息的 `data.card` MUST 含与 `question_card` 事件一致的公开投影

#### Scenario: Reload does not duplicate card

- **GIVEN** 同一题卡已存在于会话历史
- **WHEN** 前端同时拿到 `pending_question` 与历史 `data.card`
- **THEN** 页面 MUST 只渲染一张同 `question_id` 的题卡

### Requirement: Deep Question Capability

WS 回合 SHALL 接受 `capability="deep_question"`，并用可选的 `num_questions`、`difficulty`、
`question_types` 约束生成。未知 capability MUST 返回 `error`，MUST NOT 静默回退到聊天。

#### Scenario: Deep question turn is dispatched

- **GIVEN** 一条带 `capability=deep_question` 的 WS 请求
- **WHEN** 回合开始
- **THEN** 系统 MUST 走题卷生成，而不是 `mastery_path` 或 `chat`

#### Scenario: Unknown capability is rejected

- **GIVEN** 一条 `capability=no_such_capability` 的 WS 请求
- **WHEN** 回合开始
- **THEN** 系统 MUST 返回 `error` 事件，且 MUST NOT 执行任何工具或写库

### Requirement: Quiz Set Events

`deep_question` 回合 SHALL 依次发出 `quiz_plan`、每个题目的 `quiz_question`、终态 `quiz_set`
与 `turn_end`。事件 MUST NOT 含答案键；失败时 MUST 发 `error` 并以 `turn_end` 结束。

#### Scenario: Quiz set event order

- **GIVEN** 一次成功生成 2 道题的回合
- **WHEN** 消费 WS 事件
- **THEN** 顺序 MUST 为 `quiz_plan` → `quiz_question` × 2 → `quiz_set` → `turn_end`

#### Scenario: Generation failure ends the turn

- **GIVEN** 模型连续返回非法 JSON
- **WHEN** 生成失败
- **THEN** 系统 MUST 发 `error` 与 `turn_end`，且 MUST NOT 发 `quiz_set` 或落库

### Requirement: Mastery Answer And Skip Turns

WS 回合 SHALL 接受 `mastery_answer` 与 `mastery_skip`。二者存在时，系统 MUST 在启动导师
LLM 之前完成判分或跳过，并以 `tool_result` 事件发出结果（metadata 键分别为
`mastery_grade` / `mastery_skip`）。作答文本 MUST 作为本轮用户消息；导师随后 SHALL 正常流式回复。
回合 MUST 携带此前的会话历史（role + content）。

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
