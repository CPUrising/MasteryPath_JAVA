# Spec Delta: agent-turn

## ADDED Requirements

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
