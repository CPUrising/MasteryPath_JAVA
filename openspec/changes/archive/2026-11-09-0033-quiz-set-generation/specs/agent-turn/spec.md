# Spec Delta: agent-turn

## ADDED Requirements

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
