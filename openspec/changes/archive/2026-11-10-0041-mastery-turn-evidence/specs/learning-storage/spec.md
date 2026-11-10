# learning-storage Specification

## MODIFIED Requirements

### Requirement: Append Only Events

系统 SHALL 在写事务内追加领域事件，事件的 `id` 与 `revision` MUST 单调递增；历史事件 MUST NOT 被修改。事件 MUST 只在提交成功时发出。由学习者回合产生的领域事件 SHALL 携带该回合的 `session_id` 与 `turn_id`；没有回合上下文的路径级事件 MAY 留空串。

#### Scenario: TC-B7 event carries post commit revision

- **GIVEN** 一次成功 `mutate`
- **WHEN** 查询事件表
- **THEN** 新增事件 MUST 含 `path_id` 与提交后的 `revision`

#### Scenario: TC-B8 events are append only

- **GIVEN** 已有事件
- **WHEN** 后续读取
- **THEN** 历史事件 MUST 不变

#### Scenario: TC-B15 state and events commit together

- **GIVEN** `mutate` 内抛异常
- **WHEN** 执行
- **THEN** 状态与事件 MUST 同时不落库

#### Scenario: TC-I4 TC-I5 TC-I6 event stream recovery

- **GIVEN** 事件流存在历史
- **WHEN** 按 `since` 拉取
- **THEN** 系统 MUST 返回该 revision 之后的全部事件，且能重建字段级差异
- **AND** 一次失败提交 MUST NOT 产生事件，随后成功提交的 `revision` MUST 连续

#### Scenario: Turn events carry session and turn

- **GIVEN** 学习者在会话 `s1` 的回合 `t1` 中提交作答并完成判分
- **WHEN** 读取 `attempt.graded` 事件
- **THEN** 其 `session_id` MUST 为 `s1`、`turn_id` MUST 为 `t1`
- **AND** `question.registered`、`objective.mastered`、`question.skipped`、`objective.explained`、`qualitative.assessed` MUST 同样携带该回合标识
