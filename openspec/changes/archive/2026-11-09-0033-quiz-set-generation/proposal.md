# Proposal: 生成型练习题卷（deep_question 后端）

## Why

MasteryPath 目前只有掌握式单题：`mastery_quiz` 一次一题、判分即结束，适合 gate，不适合“给我一套练习题”这种生成型需求。
DeepTutor 的 `deep_question` 能力在本项目缺失。本 change 先补齐后端：一条回合生成 N 道练习题、落库、公开投影、整卷提交与确定性判分；前端 QuizViewer 留给 `0034`。

## What Changes

- 新增 `deep_question` 回合能力：WS 请求可显式选择；生成 1–10 道题（choice/short/open），最多一次修复。
- 新增增量表 `quiz_sets` / `quiz_items`（现有表 DDL 不动）；删除路径时级联清理。
- 新增只读投影：提交前 MUST NOT 含 `expected_answer` / `explanation` / `accepted_answers` / 关键词。
- 新增 REST：
  - `GET /learning/paths/{pathId}/quiz-sets/{setId}`；
  - `POST /learning/paths/{pathId}/quiz-sets/{setId}/submit`（批量确定性判分，幂等）。
- 新增 WS 事件：`quiz_plan` / `quiz_question` / `quiz_set`。
- 练习卷**不写** `interactions` / `learning_evidence` / `mastery_levels` / `pending_question`，不打开掌握 gate。

## Capabilities

### New Capabilities

- `quiz-set`: 生成、存储、公开投影、整卷提交与确定性判分。

### Modified Capabilities

- `agent-turn`: 新增 `deep_question` 能力分派与 `quiz_plan` / `quiz_question` / `quiz_set` 事件。

## Impact

- Java：`storage/Schema`、`storage/LearningStore`、`domain/model/QuizSet|QuizItem`、
  `domain/quizset/*`、`service/QuizSetService`、`adapter/turn/QuizSetTurn|QuizSetGenerator`、
  `adapter/web/QuizSetController|WsTurnHandler|TurnRunner`、`service/LearningService`。
- 数据：新增两张表；旧库可直接打开，旧表结构不变。
- 测试：存储、服务、REST、回合、生成器；全量回归。
- 不影响：前端（`0034`）、掌握判分与 SM-2、现有 `mastery_quiz` 单槽位。
