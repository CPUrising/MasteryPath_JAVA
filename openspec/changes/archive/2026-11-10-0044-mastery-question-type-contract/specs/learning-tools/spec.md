# learning-tools Specification

## ADDED Requirements

### Requirement: Short Answer Length Contract

系统 SHALL 在注册 `short` 题时校验答案长度：归一化后的 `expected_answer` 超过 30 字时，
MUST 至少提供一条归一化后不超过 30 字的 `accepted_answers`，否则 MUST 拒绝注册并提示改用
`open`（`required_keywords` / `optional_keywords`）或补充短等价答案。`choice` 与 `open` MUST NOT
受此校验影响。

#### Scenario: Long short without a short equivalent is refused

- **GIVEN** 一道 `short` 题，期望答案是超过 30 字的自然语言解释，且未提供 `accepted_answers`
- **WHEN** 注册题目
- **THEN** 系统 MUST 拒绝，并提示改用 `question_type=open` 或补充不超过 30 字的等价答案

#### Scenario: Long short with a short equivalent is accepted

- **GIVEN** 一道 `short` 题，期望答案超过 30 字，但 `accepted_answers` 含一条不超过 30 字的等价值
- **WHEN** 注册题目
- **THEN** 系统 MUST 允许注册

#### Scenario: Open accepts long expected answers

- **GIVEN** 一道 `open` 题，`expected_answer` 超过 30 字
- **WHEN** 注册题目
- **THEN** 系统 MUST 允许注册
