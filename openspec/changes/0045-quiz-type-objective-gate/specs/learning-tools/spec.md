# learning-tools Specification

## ADDED Requirements

### Requirement: Quiz Type Matches The Objective Gate

系统 SHALL 在注册题目时校验题型与目标门槛是否匹配。目标门槛为 `quantitative`（`memory` /
`procedure`）时，`mastery_quiz` MUST 拒绝 `question_type=open`，且 MUST NOT 登记 interaction、
MUST NOT 生成题卡。拒绝文案 MUST 指向 `choice`、期望答案不超过 30 字的 `short`
（可配 `accepted_answers`），并说明 `concept` / `design` 由 `mastery_assess` 打开。
`choice` 与 `short` MUST NOT 受此校验影响；`concept` / `design` 上的 `open` MUST 仍然允许。

#### Scenario: Open is refused on a memory objective

- **GIVEN** 知识点类型为 `memory`
- **WHEN** 以 `question_type=open` 注册题目
- **THEN** 系统 MUST 拒绝
- **AND** 文案 MUST 含 `question_type=choice` 与 `question_type=short`
- **AND** 该路径 MUST NOT 产生待答题

#### Scenario: Open is refused on a procedure objective

- **GIVEN** 知识点类型为 `procedure`
- **WHEN** 以 `question_type=open` 注册题目
- **THEN** 系统 MUST 拒绝，且 MUST NOT 写入 interaction

#### Scenario: Choice and short remain available on quantitative objectives

- **GIVEN** 知识点类型为 `memory` 或 `procedure`
- **WHEN** 以 `question_type=choice` 或 `question_type=short` 注册题目
- **THEN** 系统 MUST 允许注册并返回 `status=registered`

#### Scenario: Open remains available on qualitative objectives

- **GIVEN** 知识点类型为 `concept` 或 `design`
- **WHEN** 以 `question_type=open` 注册长答案题目
- **THEN** 系统 MUST 允许注册

## MODIFIED Requirements

### Requirement: Repair Recomputes Instead Of Deleting

系统 SHALL 通过作废或改正题目来修复错误评估，MUST NOT 物理删除历史证据。作废后 MUST 重算掌握度与复习状态；改正后 MUST 重判已存作答并追加新证据。改正后重判若为 `ungraded`，系统 MUST 拒绝该次修复并返回可读原因，MUST NOT 写入「已纠正」。返回体 MUST 含 `is_correct` 与 `result`，并 MUST 含 `effective`：本次修复是否可能改变掌握度。`void` 的 `effective` MUST 为 `true`；`correct` 且题型为 `open` 时 MUST 为 `false`，并 MUST 附可读说明，因为 open 判对仍降为 `partial`（`INV-35`）。工具回执 MUST 按 `effective` 如实表述，MUST NOT 暗示 open 题已恢复掌握度。

#### Scenario: TC-M5 repair void

- **GIVEN** 一条错误判分证据
- **WHEN** 作废该题
- **THEN** 证据 MUST 被标记作废、门槛 MUST 重新计算，历史记录 MUST 仍可查

#### Scenario: Corrected key still ungradable is refused

- **GIVEN** 题目已判分，且新答案键与已存作答仍无法判定
- **WHEN** 以 `correct` 修复
- **THEN** 系统 MUST 抛前置条件错误，且 MUST NOT 写 `assessment.corrected`
- **AND** `mastery_repair_question` MUST NOT 回「已纠正」

#### Scenario: Corrected key is reported truthfully

- **GIVEN** 新答案键使已存作答可以判定
- **WHEN** 以 `correct` 修复
- **THEN** 返回体 MUST 含真实的 `is_correct` 与 `result`
- **AND** 工具回执 MUST 带出该 `result`

#### Scenario: Open repair reports no credit change

- **GIVEN** 一道 `open` 题，已存作答在改键后判对
- **WHEN** 以 `correct` 修复
- **THEN** 返回体 MUST 为 `result=partial`、`is_correct=false`
- **AND** `effective` MUST 为 `false`，并 MUST 附 `credit_note`
- **AND** 该知识点的掌握度 MUST NOT 改变
