# learning-tools Specification

## ADDED Requirements

### Requirement: Status Last Answered Evidence

`mastery_status` 与 REST 路径状态 SHALL 返回 `last_answered`，回放最近一次未作废的作答证据：
`question_id`、`knowledge_point_id`、`answer`、`result`、`assessment_type`、`hints_used`、
`mastery_after`、`session_id`、`turn_id`。从未作答时 MUST 为 `{status: "none"}`。
该投影 MUST NOT 含 `expected_answer` 或 `explanation`。

#### Scenario: No attempt yields none

- **GIVEN** 一条从未产生作答的路径
- **WHEN** 读取 `mastery_status`
- **THEN** `last_answered` MUST 为 `{status: "none"}`

#### Scenario: Latest attempt is replayed

- **GIVEN** 学习者在会话 `s1` 的回合 `t1` 中答对题 `q1`
- **WHEN** 读取 `mastery_status`
- **THEN** `last_answered` MUST 含 `question_id=q1`、`answer`、`result=correct`
- **AND** MUST 含 `session_id=s1`、`turn_id=t1` 与判分后的 `mastery_after`
