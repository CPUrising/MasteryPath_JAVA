# Design: 生成型练习题卷

## 1. 范围与边界

- 练习卷是**练习**，不是掌握 gate：不写 `interactions` / `learning_evidence` / `mastery_levels` /
  `pending_question`，不调用 `Grading` 之外的模型。
- 后端本 change 完成；前端 QuizViewer 留给 `0034`。
- 不复用 `question_bank` 存未提交题目：`GET /questions` 会返回 `correct_answer`，会泄漏答案。

## 2. 存储

新增增量表（现有表逐字不动）：

```sql
CREATE TABLE IF NOT EXISTS quiz_sets (
    set_id     TEXT PRIMARY KEY,
    path_id    TEXT NOT NULL,
    session_id TEXT NOT NULL DEFAULT '',
    turn_id    TEXT NOT NULL DEFAULT '',
    status     TEXT NOT NULL DEFAULT 'open',
    data       TEXT NOT NULL,
    created_at REAL NOT NULL,
    updated_at REAL NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_quiz_sets_path ON quiz_sets(path_id);

CREATE TABLE IF NOT EXISTS quiz_items (
    item_id    TEXT PRIMARY KEY,
    set_id     TEXT NOT NULL,
    seq        INTEGER NOT NULL,
    data       TEXT NOT NULL,
    created_at REAL NOT NULL,
    updated_at REAL NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_quiz_items_set ON quiz_items(set_id, seq);
```

`QuizSet` 只存集合元数据；`QuizItem` 只存单题（含答案键）。两者都以 JSON `data` 落库，
与 `paths` / `interactions` / `question_bank` 的存储风格一致。`deletePath` 级联删除两张表。

## 3. 领域模型

- `QuizItem`：`item_id`、`set_id`、`seq`、`PendingQuestion question`、`user_answer`、`is_correct`、`result`。
  复用 `PendingQuestion` 固定答案键，判分直接复用 `Grading`。
- `QuizSet`：`set_id`、`path_id`、`session_id`、`turn_id`、`title`、`topic`、`status`、
  `question_count`、`score`、`created_at`、`submitted_at`。
- `QuizSetProjector`（纯函数）：
  - 提交前：`question_id` / `seq` / `prompt` / `question_type` / `options` / `difficulty`；
  - 提交后：追加 `learner_answer` / `is_correct` / `result` / `correct_answer` / `explanation`。
- `QuizSetGrader`（纯函数）：`QuizItem + answer → Decision`，复用 `Grading.grade`；
  练习卷不复制掌握题的“open 降级为 partial”，`correct` 保持 `correct`。

## 4. 生成

`QuizSetGenerator` 在 adapter 层调用 `LlmClient.chat`：

1. 一次结构化生成调用，要求返回 `{"questions":[...]}`；每题含
   `question_type`、`question`、`options`、`expected_answer`、`accepted_answers`、
   `required_keywords`、`optional_keywords`、`explanation`、`difficulty`。
2. 用 `QuizSpec.normalize` 校验；short 的自然语言答案要求 `accepted_answers`，
   open 的关键词沿用 `QuizSpec.openRequiredKeywords`。
3. 任一题失败 → 带错误信息修复一次；仍失败 → `QuizGenerationException`，零落库。
4. 全部通过后才构造 `QuizSet` + `QuizItem` 并原子落库。

> 与 DeepTutor 的 `planning → quizzing` 多阶段相比，本 change 先做单次结构化生成；
> 可观察契约（N 道题、逐题事件、校验/修复、失败零落库）保持一致，减少模型调用与延迟。
> 后续若需要“先出计划再逐题生成”，可在不改变存储/事件契约的前提下替换生成器。

## 5. 回合与事件

`TurnRequest` 增加：

- `capability = "deep_question"`
- `numQuestions`（默认 3，clamp 1–10）
- `difficulty`（`easy/medium/hard/auto`，可空）
- `questionTypes`（`choice/short/open` 子集，可空）

`WsTurnHandler` 读取 `capability` 与上述字段；`TurnRunner.run` 显式分派：

```
mastery_path  → MasteryTurn
deep_question → QuizSetTurn
chat          → ChatTurnAdapter
其他          → UnsupportedOperationException
```

`QuizSetTurn` 在第一次 `hasNext()` 时完成生成与落库，然后依次产出：

```
quiz_plan {set_id, topic, count, templates}
quiz_question × N {set_id, question_id, seq, prompt, question_type, options, difficulty}
quiz_set {public set + questions}
turn_end {reason: "quiz_set"}
```

失败时只产出 `error` + `turn_end`，不产出 `quiz_set`。落库后调用
`LearningService.recordQuizSetTurn`，把 user 消息与 assistant `data.quiz_set` 写入会话，
刷新后可恢复。

## 6. REST

- `GET /learning/paths/{pathId}/quiz-sets/{setId}`：校验 set 属于 path；返回公开投影。
- `POST /learning/paths/{pathId}/quiz-sets/{setId}/submit`：
  body `{"answers":[{"question_id":"...","answer":"..."}]}`；
  - 全部可判定 → 更新 item 结果、`status=submitted`、`score`、`submitted_at`，返回提交后投影；
  - 任一 `ungraded` → 返回 `status=ungraded` 与逐题原因，零状态变更；
  - 已提交 → 直接返回首次结果。
- 未知 set / path 不匹配 → 404；无答案 / 非法 body → 422。

## 7. 风险与取舍

| 风险 | 处理 |
|---|---|
| 生成未提交题目泄漏答案 | 公开投影白名单；只从 `quiz_items` 读答案 |
| 半套题落库 | 全部校验通过后一次事务写入 |
| 模型 JSON 不稳定 | 严格解析 + 一次修复 + 失败零落库 |
| 练习卷污染掌握 | 不写 interactions/evidence/mastery/pending |
| 旧库兼容 | 只新增表，旧表 DDL 不动；旧库打开时 `CREATE IF NOT EXISTS` |
| 重复提交 | `status=submitted` 短路，返回首次结果 |

## 8. 测试计划

- `LearningStore`：保存/读取题卷、提交结果、删除路径级联。
- `QuizSetService`：提交正确/部分/错误、ungraded 零变更、重复提交幂等、掌握度不变。
- `QuizSetProjectorTest`：提交前无答案键；提交后释放答案。
- `QuizSetTurnTest`：FakeLlm 成功生成、事件顺序、非法 JSON 失败零落库、未知 capability 报错。
- `QuizSetApiTest`：GET 投影、submit 200/422/404、重复提交。
- 全量 `verify.ps1` 回归。
