# Proposal: 题库按知识库分类

## Why

题库条目只记录 `path_id` 与 `knowledge_point_id`，没有知识库归属。学习路径可以同时绑定多个知识库
（`paths.data → sources[]` 中 `kind == "knowledge_base"` 的条目），学习者在题库里无法按「这条题是从哪个知识库来的」来检索、复盘与刷题。

## What Changes

- 题库条目增加**派生**字段 `knowledge_bases`（`[{id, name}]`）：由条目所属路径绑定的知识库解析而来，
  **不写入 `question_bank.data`**、不新增表/列。
- 一道题可属于多个知识库（路径绑定多个库时全部归属）；路径未绑定或已删除时归「未关联」。
- 新增 `GET /questions/kb-groups`：按知识库聚合题目数（含未关联组）。
- `GET /questions/search`、`GET /questions/practice` 新增 `knowledge_base_ids` 参数，**OR 语义**（命中任一）。
- 刷题卡投影新增 `knowledge_bases`，仍严格隐藏答案键。
- Space 题库面板新增知识库 chips 与分组、行内知识库徽章。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `question-bank`：新增「按知识库归属与筛选」的对外契约。

## Impact

- Java：`domain/model/KnowledgeBaseRef`（新增）、`QuestionBankEntry`（派生字段）、
  `domain/bank/QuestionBankKnowledgeBases`（新增纯函数）、`QuestionBankService`、
  `QuestionBankProjector`、`QuestionBankPracticeController`。
- 前端：`web/lib/types.ts`、`web/lib/api.ts`、`QuestionBankPanel`、`PracticeRunner`、`i18n`。
- 数据：零 DDL、零迁移；参考实现旧库可直接打开。
- 行为：`GET /questions` 输出保持 17 键不变；新增能力只出现在 `search` / `practice` / `kb-groups`。
