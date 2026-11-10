# learning-tools Specification

## MODIFIED Requirements

### Requirement: Deterministic Grading

系统 SHALL 在注册时固定参照物，判分时刻 MUST NOT 调用 LLM。判分 MUST 按幂等键
`(path_id, interaction_id)` 定位事务：已判分时 MUST 返回首次结果且不新增证据。
判分结果 SHALL 为 `correct` / `incorrect` / `partial` / `ungraded`。**无法解析的作答 MUST 返回
`ungraded` 且零状态变更**：MUST NOT 写 attempt 或学习证据、MUST NOT 改变掌握度、MUST NOT 清空待答题。
开放题判对 MUST 在服务层降为 `partial`（`INV-35`）。有提示的作答 MUST 记录
`independent=false`，结果 MUST NOT 被降级。

- `choice`：先按标签命中；否则按选项正文归一化后**唯一**匹配；解析不出 → `ungraded`。
- `short`：精确 → `accepted_answers` → 数值容差 1e-6 → 相似度 ≥0.90；超过 30 字只做精确匹配；
  空作答 → `ungraded`。
- `open`：使用注册时固定的 `required_keywords` / `optional_keywords`；required 全中且 optional
  命中率 ≥0.6 判对；缺 required 但有命中 → `partial`；未注册关键词 → `ungraded`。

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
- **THEN** 结果 MUST 为 `ungraded`
- **AND** 系统 MUST NOT 写入 attempt / 证据，掌握度与待答题 MUST 保持不变

#### Scenario: TC-H1 body unique match

- **GIVEN** 选择题期望答案为标签 `B`，且 `B` 的正文唯一
- **WHEN** 作答为选项正文 / ` b ` / `B`
- **THEN** 三种作答 MUST 均判 `correct`

#### Scenario: TC-H3 TC-H4 short answer matching

- **GIVEN** 简答题
- **WHEN** 作答与期望答案完全一致 / 相似度低于 0.90 / 期望答案超过 30 字且不完全一致
- **THEN** 结果 MUST 依次为正确、错误、错误
- **AND** 期望答案 ≤30 字且相似度 ≥0.90 时 MUST 判对
- **AND** 空作答 MUST 返回 `ungraded`

#### Scenario: TC-H5 open answer is weakest

- **GIVEN** 开放题注册了 required 与 optional 关键词
- **WHEN** required 全中且 optional 命中率达到 0.6 / 低于 0.6 / required 未全中
- **THEN** 结果 MUST 依次为 `correct`、`partial`、`incorrect` 或 `partial`
- **AND** 服务层 MUST 把开放题的 `correct` 降为 `partial`

#### Scenario: TC-K5 TC-H6 hints downgrade

- **GIVEN** 作答时使用了提示
- **WHEN** 判分
- **THEN** 结果 MUST 仍为 `correct`，但作答记录 MUST 标记 `independent=false`
- **AND** 结果 MUST NOT 被降级为 `partial`

### Requirement: Repair Recomputes Instead Of Deleting

系统 SHALL 通过作废或改正题目来修复错误评估，MUST NOT 物理删除历史证据。作废后 MUST 重算掌握度与复习状态；改正后 MUST 重判已存作答并追加新证据。改正后重判若为 `ungraded`，系统 MUST 拒绝该次修复并返回可读原因，MUST NOT 写入「已纠正」。返回体 MUST 含 `is_correct` 与 `result`。

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

### Requirement: Grade Feedback Persistence

`POST /learning/paths/{id}/grade` SHALL 接受可选 `session_id` 与 `turn_id`。只有真正 `graded`
的结果 MUST 以 user 答案 + assistant `data.grade` 写入会话消息；`ungraded` MUST 返回 422、
MUST NOT 写入会话消息、MUST NOT 改变掌握度或证据。同一 `question_id` 的重复判分 MUST NOT 重复写入消息。

#### Scenario: Graded feedback persists

- **GIVEN** 一次带 `session_id` 的题卡作答且判分成功
- **WHEN** 重新读取会话详情
- **THEN** 消息 MUST 同时包含用户答案与 assistant 的 `data.grade`，且含 `result` 与 `is_correct`

#### Scenario: Ungraded persists with zero change

- **GIVEN** 一次无法解析的作答
- **WHEN** 判分返回 `ungraded`
- **THEN** 响应 MUST 为 422，会话 MUST NOT 新增消息
- **AND** 掌握度、证据与待答题 MUST 不变

#### Scenario: Repeated grade does not duplicate messages

- **GIVEN** 同一 `question_id` 已判分并已落库
- **WHEN** 再次提交同一作答
- **THEN** 系统 MUST 返回首次结果，且会话消息 MUST NOT 新增重复记录

## REMOVED Requirements

### Requirement: Short Questions Need Accepted Answers

**Reason**: 参考实现的注册期不强制 `accepted_answers`；判分器现在真正读取它，
缺失时退化为精确/模糊匹配即可，无需在注册时拒绝。

**Migration**: 模型未提供 `accepted_answers` 时，简答题按精确 → 数值 → 相似度判分；
需要同义表述判定时由模型自行补充。
