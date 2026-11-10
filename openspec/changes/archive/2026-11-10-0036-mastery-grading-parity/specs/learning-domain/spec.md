# learning-domain Specification

## ADDED Requirements

### Requirement: Recency-Weighted Mastery Score

系统 SHALL 用最近 5 次、按时间加权的正确率计算 `memory` / `procedure` 的掌握度：
权重为 `(0.5, 0.7, 0.85, 0.95, 1.0)`（越新越大）；且 MUST 施加置信上限：
仅 1 次作答时分数不超过 0.5，仅 2 次时不超过 0.8。统计 MUST 只包含
`independent=true` 且未作废的作答。

#### Scenario: Single correct answer is capped

- **GIVEN** 一个 `memory` 知识点只有 1 次独立正确作答
- **WHEN** 计算掌握度
- **THEN** 分数 MUST 为 0.5，且 MUST NOT 达到 0.9 门槛

#### Scenario: Two correct answers are capped

- **GIVEN** 2 次独立正确作答
- **WHEN** 计算掌握度
- **THEN** 分数 MUST 为 0.8，且 MUST NOT 达到 0.9 门槛

#### Scenario: Three correct answers clear the gate

- **GIVEN** 3 次独立正确作答
- **WHEN** 计算掌握度
- **THEN** 分数 MUST 达到 1.0 并清除门槛

#### Scenario: Hints and voids are excluded

- **GIVEN** 一次正确但 `independent=false` 的作答，以及一次作废的作答
- **WHEN** 计算掌握度
- **THEN** 两者 MUST NOT 计入

#### Scenario: Recency weights recover from early mistakes

- **GIVEN** 作答序列为 错、错、错、对、对
- **WHEN** 计算掌握度
- **THEN** 分数 MUST 按最近 5 次权重计算，且最新一次权重为 1.0
