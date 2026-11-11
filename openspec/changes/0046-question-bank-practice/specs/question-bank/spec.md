# question-bank Specification

## Purpose
定义题库查询、标签管理与刷题判分的对外契约：题库可跨路径检索，标签可全局管理，刷题复用确定性判分且不影响掌握闭环。

## ADDED Requirements

### Requirement: Question Bank Search

系统 SHALL 支持按知识路径、关键字、结果与多标签筛选题库条目；多标签 MUST 为 AND 语义。
省略 `path_id` 时 MUST 跨全部学习路径聚合。响应 MAY 含答案字段（这是管理/复盘视图）。

#### Scenario: Multi-tag filter is conjunctive

- **GIVEN** 条目 A 打标签 `java`、`并发`，条目 B 只打 `java`
- **WHEN** 以 `tags=java,并发` 查询
- **THEN** 只返回条目 A

#### Scenario: Wrong results filter

- **GIVEN** 题库中同时存在 `correct` 与 `incorrect`
- **WHEN** 以 `result=wrong` 查询
- **THEN** 返回 `incorrect` / `partial` / `voided`，不返回 `correct`

### Requirement: Derived Tag Summary

系统 SHALL 从题库条目的 `tags` 派生标签汇总，返回标签名与题目数；MUST 忽略 `null` 与空白标签。
排序 MUST 为题目数降序、标签名升序。MUST NOT 新增标签注册表或修改 DDL。

#### Scenario: Counts are derived

- **GIVEN** 3 个条目分别打 `java`、`java`、`并发`
- **WHEN** 读取 `/questions/tags`
- **THEN** `java` 的计数为 2、`并发` 的计数为 1

### Requirement: Global Tag Management

系统 SHALL 支持标签重命名、合并、删除与批量打标；操作 MUST 在工作区全部条目上生效。
替换结果 MUST 去重并保持首次出现顺序。空白标签名 MUST 以 422 拒绝。

#### Scenario: Rename merges duplicates

- **GIVEN** 某条目标签为 `["a","b"]`，另一条为 `["b"]`
- **WHEN** 将 `a` 重命名为 `b`
- **THEN** 两条条目均为 `["b"]`，且返回变更条目数

#### Scenario: Delete removes everywhere

- **GIVEN** 多个条目含标签 `临时`
- **WHEN** 删除 `临时`
- **THEN** 所有条目的 `tags` 不再含该标签

#### Scenario: Batch applies add then remove

- **GIVEN** 选中 2 个条目
- **WHEN** 批量添加 `重点`、移除 `草稿`
- **THEN** 两个条目都含 `重点` 且都不含 `草稿`

### Requirement: Practice Queue Hides Answers

刷题队列 MUST 只返回可确定性判分的题目：`choice` / `short`，且 `correct_answer` 非空白；
`choice` 的答案 MUST 能匹配某个选项标签。`open` / `written` MUST 排除。

队列投影 MUST 为白名单，且 MUST NOT 含 `correct_answer`、`explanation`、`user_answer`、`is_correct`。
数量 MUST 夹取在 1–50（默认 10），排序 MUST 支持 `newest` / `wrong_first` / `random`。

#### Scenario: Written questions are excluded

- **GIVEN** 题库含 `written` 与 `short` 两种条目
- **WHEN** 请求刷题队列
- **THEN** 只返回 `short` 条目，且不含答案字段

#### Scenario: Size is bounded

- **GIVEN** 请求 `size=0` 或 `size=100`
- **WHEN** 规范化
- **THEN** 实际数量落在 1–50

#### Scenario: Wrong first ordering

- **GIVEN** 队列中既有 `is_correct=false` 也有 `is_correct=true`
- **WHEN** 以 `order=wrong_first` 请求
- **THEN** 错误条目排在正确条目之前

### Requirement: Deterministic Bank Attempt

`POST /questions/{entryId}/attempt` SHALL 使用确定性判分，MUST NOT 调用 LLM。
判定成功时 MUST 只回写题库条目的 `user_answer`、`is_correct`、`result`（及 `updated_at`）；
`ungraded` 时 MUST 零写入。
该操作 MUST NOT 修改掌握度、证据、待答题目、复习队列，MUST NOT 写事件或交互。

#### Scenario: Attempt updates only the bank entry

- **GIVEN** 一条路径存在掌握度与待答题目
- **WHEN** 对题库条目提交正确答案
- **THEN** 条目 `result=correct`，且路径 `version`、`mastery_levels`、`pending_question`、
  `repetition_states` 与事件数量均不变

#### Scenario: Ungraded writes nothing

- **GIVEN** `short` 题的作答无法解析（空作答）
- **WHEN** 提交
- **THEN** 返回 `status=ungraded`，条目 `user_answer` / `result` MUST 不变

#### Scenario: Unknown entry is rejected

- **GIVEN** 不存在的 `entryId`
- **WHEN** 提交作答
- **THEN** 返回 404，文案为 `unknown question bank entry: {id}`

#### Scenario: Non-practiceable question is rejected

- **GIVEN** 一条 `written` 题型条目
- **WHEN** 提交作答
- **THEN** 返回 422，文案为 `question is not practiceable: {id}`

### Requirement: Additive Storage

本能力 MUST NOT 修改 `paths` / `events` / `interactions` / `question_bank` / `sessions` /
`messages` / `quiz_sets` / `quiz_items` 的 DDL，也 MUST NOT 新增表。
标签管理 MUST 仅依赖 `question_bank.data` 的 `tags` 字段。

#### Scenario: Reference database still opens

- **GIVEN** 参考实现生成的旧 `learning.db`
- **WHEN** Java store 打开并执行题库操作
- **THEN** 表结构不变，读写成功
