# learning-tools Specification

## MODIFIED Requirements

### Requirement: Deterministic Grading

系统 SHALL 在注册时固定参照物，判分时刻 MUST NOT 调用 LLM。判分 MUST 按幂等键
`(path_id, interaction_id)` 定位事务：已判分时 MUST 返回首次结果且不新增证据。
判分结果只有 `correct` / `incorrect`：`expected_answer` 为空 MUST 判错（fail-closed）；
无法解析的作答 MUST 判错，MUST NOT 返回 `ungraded` 或 `partial`。
有提示的作答 MUST 记录 `independent=false`，但结果 MUST NOT 被降级。

- `choice`：去掉空格后整串相等。
- `short`：完全相等；期望答案长度 ≤30 时相似度 ≥0.85；超过 30 字只做精确匹配。
- `open`：期望答案按 `[,;，；。\n]` 拆词，命中率 ≥0.6 判对。

#### Scenario: TC-K1 registration validation

- **GIVEN** 选择题选项体重复 / 期望答案无法解析为标签 / 简答题带选项
- **WHEN** 注册题目
- **THEN** 系统 MUST 拒绝

#### Scenario: TC-K3 TC-E3 idempotent grading

- **GIVEN** 同一 `question_id` 已判分
- **WHEN** 重复判分
- **THEN** 系统 MUST 返回首次结果且证据条数 MUST 不变

#### Scenario: TC-K4 TC-H2 ungraded zero change

- **GIVEN** 选择题期望答案为 `B`
- **WHEN** 作答为 `zzz`（无法解析为标签）
- **THEN** 结果 MUST 为 `incorrect`，且 MUST 正常写入作答与证据
- **AND** 系统 MUST NOT 返回 `ungraded`

#### Scenario: TC-H1 body unique match

- **GIVEN** 选择题期望答案为标签 `B`
- **WHEN** 作答为选项正文而非标签
- **THEN** 结果 MUST 为 `incorrect`；作答为 ` b ` 或 `B` 时 MUST 为 `correct`

#### Scenario: TC-H3 TC-H4 short answer matching

- **GIVEN** 简答题
- **WHEN** 作答与期望答案完全一致 / 相似度低于 0.85 / 期望答案超过 30 字且不完全一致
- **THEN** 结果 MUST 依次为正确、错误、错误
- **AND** 期望答案 ≤30 字且相似度 ≥0.85 时 MUST 判对

#### Scenario: TC-H5 open answer is weakest

- **GIVEN** 开放题期望答案含 5 个以分隔符隔开的关键词
- **WHEN** 作答命中 3 个（0.6）
- **THEN** MUST 判对；命中少于 3 个 MUST 判错

#### Scenario: TC-K5 TC-H6 hints downgrade

- **GIVEN** 作答时使用了提示
- **WHEN** 判分
- **THEN** 结果 MUST 仍为 `correct`，但作答记录 MUST 标记 `independent=false`
- **AND** 结果 MUST NOT 被降级为 `partial`
