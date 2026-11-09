# Spec Delta: learning-tools

## ADDED Requirements

### Requirement: Short Questions Need Accepted Answers

系统 SHALL 在注册 `short` 题时要求至少一个 `accepted_answers`；纯数值或单 token 的 `short`
MAY 豁免。缺失时 MUST 拒绝并返回可读原因，MUST NOT 静默退化为仅精确匹配。`open` 题继续使用
既有 `required_keywords` / `optional_keywords` 契约，不受本要求影响。

#### Scenario: Natural-language short needs accepted answers

- **GIVEN** 一道 `short` 题，期望答案是自然语言短语，且未提供 `accepted_answers`
- **WHEN** 注册题目
- **THEN** 系统 MUST 拒绝，并提示补充可接受答案

#### Scenario: Numeric short is exempt

- **GIVEN** 一道 `short` 题，期望答案是纯数值或单 token
- **WHEN** 注册题目
- **THEN** 系统 MUST 允许注册

#### Scenario: Short with accepted answers is accepted

- **GIVEN** 一道 `short` 题，期望答案是自然语言短语，且提供了至少一个 `accepted_answers`
- **WHEN** 注册题目
- **THEN** 系统 MUST 允许注册

### Requirement: Grade Feedback Persistence

`POST /learning/paths/{id}/grade` SHALL 接受可选 `session_id` 与 `turn_id`。成功与 `ungraded`
结果 MUST 以 user 答案 + assistant `data.grade` 写入会话消息；`ungraded` MUST NOT 改变掌握度或证据；
同一 `question_id` 的重复判分 MUST NOT 重复写入消息。

#### Scenario: Graded feedback persists

- **GIVEN** 一次带 `session_id` 的题卡作答且判分成功
- **WHEN** 重新读取会话详情
- **THEN** 消息 MUST 同时包含用户答案与 assistant 的 `data.grade`，且含 `result` 与 `is_correct`

#### Scenario: Ungraded persists with zero change

- **GIVEN** 一次无法解析的作答
- **WHEN** 判分返回 `ungraded`
- **THEN** 会话 MUST 记录 `status=ungraded`，且掌握度与 `learning_evidence` MUST 不变

#### Scenario: Repeated grade does not duplicate messages

- **GIVEN** 同一 `question_id` 已判分并已落库
- **WHEN** 再次提交同一作答
- **THEN** 系统 MUST 返回首次结果，且会话消息 MUST NOT 新增重复记录
