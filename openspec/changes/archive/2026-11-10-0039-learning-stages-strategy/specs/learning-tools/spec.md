# learning-tools Specification

## ADDED Requirements

### Requirement: Status Stage And Step Metadata

`mastery_status` 与 REST 路径状态 SHALL 为每个知识点返回 `learning_stages`：
`not_yet_covered` | `explained` | `practiced_not_demonstrated` | `practiced_with_help` |
`independently_demonstrated`。`next` SHALL 带 `gate`（`qualitative` / `quantitative`）与
`forgetting_risk`。

#### Scenario: Learning stages cover every objective

- **GIVEN** 一条已建大纲的路径
- **WHEN** 读取 `mastery_status`
- **THEN** `learning_stages` MUST 以知识点 id 为键，覆盖全部目标
- **AND** 未触碰目标 MUST 为 `not_yet_covered`

#### Scenario: Next step reports its gate

- **GIVEN** 一个待推进的定量目标
- **WHEN** 读取 `next`
- **THEN** `gate` MUST 为 `quantitative`
- **AND** 定性目标的 `gate` MUST 为 `qualitative`

#### Scenario: Review step reports forgetting risk

- **GIVEN** 一个到期复习项带有非零遗忘风险
- **WHEN** `next.action` 为 `review`
- **THEN** `forgetting_risk` MUST 等于该复习项的风险值
