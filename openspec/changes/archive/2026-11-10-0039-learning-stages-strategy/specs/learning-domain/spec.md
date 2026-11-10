# learning-domain Specification

## MODIFIED Requirements

### Requirement: Frontier Advancement

系统 SHALL 以「已掌握」为唯一推进依据计算下一个目标，并 MUST 把未满足的前置知识点作为硬门控：前置未掌握时该目标 MUST NOT 被选中，也 MUST NOT 可被评估。模块级 `display_target` MUST NOT 参与推进。

计算顺序 MUST 与参考实现一致：待答题目 → 未延后的到期复习 → 第一个可选未掌握目标 → 只剩被延后目标时回退到其中一个 → `complete`。未触碰目标（`status == new`）的动作 MUST 由学习者画像的 `teaching_strategy` 决定：`teach_first` 返回 `teach`，缺省返回 `probe`；已有记录后按门槛种类返回 `assess`（定性）或 `practice`（定量）。

#### Scenario: TC-C9 TC-C10 next objective

- **GIVEN** 模块与知识点顺序固定
- **WHEN** 存在未掌握且可选的目标 / 全部已掌握或已延后
- **THEN** 系统 MUST 返回第一个可选目标 / MUST 返回 `complete`

#### Scenario: TC-C11 answer pending first

- **GIVEN** 存在未作答的待答题目
- **WHEN** 计算下一个目标
- **THEN** 系统 MUST 返回 `answer_pending`

#### Scenario: TC-F1 display target is not a gate

- **GIVEN** 模块聚合分低于 `display_target` 但所有知识点已掌握
- **WHEN** 计算下一个目标
- **THEN** 系统 MUST 返回 `complete`

#### Scenario: TC-F6 TC-F7 TC-F8 prerequisite hard gate

- **GIVEN** `design` 知识点存在未掌握的前置 `procedure`
- **WHEN** 计算下一个目标
- **THEN** 系统 MUST 返回该前置而不是 `design`
- **AND** 当前置通过但定性未通过时 `design` MUST 仍未掌握
- **AND** 当前置通过且定性通过时 `design` MUST 掌握

#### Scenario: Due review comes before new objectives

- **GIVEN** 存在一个已掌握且到期的复习项，以及一个未掌握目标
- **WHEN** 计算下一个目标
- **THEN** 系统 MUST 返回 `review`
- **AND** 该步骤 MUST 带 `forgetting_risk` 与 `gate`

#### Scenario: Deferred objectives are not reviewed

- **GIVEN** 一个知识点的复习已到期，但它被学习者延后
- **WHEN** 计算下一个目标
- **THEN** 该复习 MUST NOT 被返回

#### Scenario: Deferred objectives remain as fallback

- **GIVEN** 所有剩余未掌握目标都被延后
- **WHEN** 计算下一个目标
- **THEN** 系统 MUST 返回其中第一个，而不是 `complete`
- **AND** 该目标 MUST NOT 被视为已掌握

#### Scenario: Teaching strategy picks probe or teach

- **GIVEN** 一个未触碰目标
- **WHEN** 学习者画像的 `teaching_strategy` 为 `teach_first`
- **THEN** 动作 MUST 为 `teach`
- **AND** 未记录或为 `probe_first` 时动作 MUST 为 `probe`

#### Scenario: Stages follow the evidence

- **GIVEN** 某知识点只有非独立作答 / 无提示的独立答对 / 仅讲解
- **WHEN** 读取其学习阶段
- **THEN** 分别 MUST 为 `practiced_with_help` / `independently_demonstrated` / `explained`
- **AND** 无任何作答与证据时 MUST 为 `not_yet_covered`
