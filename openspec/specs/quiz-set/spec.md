# quiz-set Specification

## Purpose
定义生成型练习题卷的后端契约：一次回合生成一组题、答案只在服务端、整卷提交后确定性判分，并且不影响掌握闭环。

## Requirements

### Requirement: Generate A Practice Quiz Set

系统 SHALL 支持在一次 `deep_question` 回合中生成 1–10 道练习题；生成结果 MUST 先全部通过校验再原子落库。
无法解析或校验失败时 MUST 最多修复一次；仍失败 MUST 返回错误且 MUST NOT 落库半套题。

#### Scenario: Valid quiz set is generated

- **GIVEN** 一条已存在的学习路径与可用的 LLM
- **WHEN** 发起 `deep_question` 回合并请求 3 道题
- **THEN** 系统 MUST 生成 3 道通过校验的题，并原子写入 `quiz_sets` / `quiz_items`

#### Scenario: Invalid generation fails closed

- **GIVEN** 模型返回无法解析或缺少答案键的 JSON
- **WHEN** 修复一次后仍不合法
- **THEN** 系统 MUST 返回错误，且 `quiz_sets` / `quiz_items` MUST 无新增

#### Scenario: Count is bounded

- **GIVEN** 请求 0 或 100 道题
- **WHEN** 规范化请求
- **THEN** 实际题数 MUST 落在 1–10 之间

### Requirement: Quiz Set Projection Hides Answers

提交前，`GET /learning/paths/{pathId}/quiz-sets/{setId}` MUST NOT 含
`expected_answer`、`explanation`、`accepted_answers`、`required_keywords`、`optional_keywords`。
提交成功后，同一端点 MAY 返回这些字段用于复盘。

#### Scenario: Before submit no answer key

- **GIVEN** 一套已生成但未提交的题卷
- **WHEN** 读取题卷
- **THEN** 每个题目 MUST 只有 `question_id` / `prompt` / `question_type` / `options` / `difficulty` 等公开字段

#### Scenario: After submit answer key is released

- **GIVEN** 一套已提交的题卷
- **WHEN** 读取题卷
- **THEN** 每道题 MUST 含 `learner_answer`、`is_correct`、`result`、`correct_answer` 与 `explanation`

### Requirement: Deterministic Submission

`POST /learning/paths/{pathId}/quiz-sets/{setId}/submit` SHALL 使用确定性判分，MUST NOT 调用 LLM。
全部题目可判定时才落库；任一题 `ungraded` MUST 返回未判定且零状态变更。
重复提交 MUST 返回首次结果，MUST NOT 重复写。提交 MUST NOT 修改掌握度、证据或待答题槽位。

#### Scenario: All answers are graded

- **GIVEN** 一套 3 题且全部作答
- **WHEN** 提交
- **THEN** 系统 MUST 返回逐题 `correct/partial/incorrect` 与总分，并把题卷标记为 `submitted`

#### Scenario: Ungraded is all-or-nothing

- **GIVEN** 一套题中有一题无法解析
- **WHEN** 提交
- **THEN** 系统 MUST 返回 `ungraded`，题卷状态 MUST 仍为 `open`，逐题结果 MUST 不落库

#### Scenario: Repeated submit is idempotent

- **GIVEN** 一套已提交的题卷
- **WHEN** 再次提交
- **THEN** 系统 MUST 返回首次结果，且 `quiz_items` MUST NOT 新增重复记录

#### Scenario: Mastery is untouched

- **GIVEN** 一条已有掌握度与证据的路径
- **WHEN** 生成并提交一套练习题
- **THEN** `mastery_levels`、`learning_evidence` 与 `pending_question` MUST 不变

### Requirement: Additive Quiz Set Storage

系统 SHALL 用增量表 `quiz_sets` / `quiz_items` 存储题卷；现有 `paths` / `events` / `interactions` /
`question_bank` / `sessions` / `messages` 的 DDL MUST 保持不变。删除路径 MUST 级联删除其题卷。

#### Scenario: Existing database opens

- **GIVEN** 一个参考实现生成的旧 `learning.db`
- **WHEN** Java store 打开
- **THEN** 旧表结构 MUST 不变，新表 MUST 按需创建

#### Scenario: Delete path cascades quiz sets

- **GIVEN** 一条带题卷的路径
- **WHEN** 删除该路径
- **THEN** `quiz_sets` 与 `quiz_items` 中该路径的记录 MUST 一并删除
