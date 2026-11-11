# Proposal: 题库刷题与标签管理

## Why

`question_bank` 早已在判分与定性评估时写入，但产品侧只有两个只读出口：
`GET /questions`（跨路径聚合）与 `GET /learning/paths/{pathId}/questions`，前端 Space 页的
「题库」tab 只是「题干 + 答案」的静态列表。学习者无法按标签找题、无法重做旧题，标签也只能
通过 `POST /learning/questions/{entryId}/tags` 单条整体替换，没有全局的标签重命名/合并/删除。

结果是题库只是「历史记录」，不是可用的练习资产。

## What Changes

- 新增题库工作台 REST 端点（全部挂在 `/questions` 下，均为增量）：
  - `GET /questions/tags`：派生标签汇总（标签 + 题目数）。
  - `GET /questions/search`：关键字 / 结果 / 多标签（AND）筛选与分页。
  - `GET /questions/practice`：**隐藏答案**的刷题队列（可刷题判定、数量夹取、错题优先）。
  - `POST /questions/{entryId}/attempt`：确定性判分，只回写题库条目的最近一次作答。
  - `POST /questions/tags/rename|merge|delete|batch`：全局标签管理与批量打标。
- 新增 `QuestionBankService`、纯函数 `QuestionBankGrading`、白名单投影 `QuestionBankProjector`。
- Space 页「题库」tab 升级为完整工作台：筛选、单条/批量标签、标签管理弹窗、刷题器。
- **不新增数据库表、不修改任何 DDL**；标签管理派生自 `question_bank.data.tags`。
- 刷题**不影响掌握闭环**：不写 `mastery_levels` / `learning_evidence` / `pending_question` /
  `review_queue` / `events` / `interactions`，不调用 LLM。

## Capabilities

### New Capabilities

- `question-bank`：题库查询、标签管理、刷题队列与作答判分的对外契约。

### Modified Capabilities

（无。既有 `GET /questions`、`GET /learning/paths/{pathId}/questions`、
`POST /learning/questions/{entryId}/tags` 的语义保持不变。）

## Impact

- Java：`LearningStore`（两个只读查询）、新增 `domain/bank/QuestionBankGrading`、
  `domain/projection/QuestionBankProjector`、`service/QuestionBankService`、
  `adapter/web/QuestionBankPracticeController`、`WebConfig`。
- 前端：`web/lib/types.ts`、`web/lib/api.ts`、`web/components/space/*`、`web/i18n/*.json`。
- 测试：新增 4 个测试类；既有 `QuestionBankTest` / `LearningApiTest` / `RepairQuestionTest`
  的断言不变。
- 数据：无迁移、无新增列/表；参考实现的 `learning.db` 可继续直接打开。
