# Design: 练习题卷前端（QuizViewer）

## 1. 数据形状（与 `0033` 后端对齐）

`quiz_plan`：

```json
{"set_id":"...","path_id":"...","topic":"...","count":3,
 "templates":[{"question_type":"choice","difficulty":"medium"}]}
```

`quiz_question`：

```json
{"set_id":"...","question_id":"...","seq":1,"prompt":"...",
 "question_type":"choice","options":[{"label":"A","body":"..."}],"difficulty":"medium"}
```

`quiz_set` / `GET /learning/paths/{pathId}/quiz-sets/{setId}`：

```json
{"set_id":"...","path_id":"...","title":"...","topic":"...","status":"open",
 "question_count":3,"score":null,"created_at":0,"submitted_at":null,
 "questions":[{"question_id":"...","seq":1,"prompt":"...","question_type":"choice",
               "options":[...],"difficulty":"medium"}]}
```

已提交后每道题追加 `learner_answer` / `is_correct` / `result` / `correct_answer` / `explanation`。
提交响应额外带 `ungraded_reasons`（`status=ungraded` 时）。

## 2. 前端类型

`lib/types.ts` 新增：

- `QuizQuestion`：公开字段 + 可选复盘字段。
- `QuizSet`：题卷元数据 + `questions: QuizQuestion[]`。
- `QuizSetSubmitOutcome`：`status` / `score` / `questions` / `ungraded_reasons?`。
- `SessionMessage.data` 增加 `quiz_set?: QuizSet`。

## 3. API 与回合

`lib/api.ts`：

- `getQuizSet(pathId, setId): Promise<QuizSet>`
- `submitQuizSet(pathId, setId, answers): Promise<QuizSetSubmitOutcome>`

`lib/ws.ts`：`TurnRequest` 增加 `capability?: "mastery_path" | "deep_question"`、
`num_questions?: number`、`difficulty?: string`。

## 4. 交互

- 会话页 composer 增加能力芯片：`掌握辅导`（默认）/ `练习题`。选中 `练习题` 后发送时
  `capability="deep_question"`、`num_questions=3`；收到 `turn_end` 后自动回到 `掌握辅导`。
- 事件渲染：
  - `quiz_plan`：在消息流内联一条“正在生成 N 道练习题”的状态条；
  - `quiz_question`：按 `set_id` 聚合，逐题追加；
  - `quiz_set`：把聚合结果替换为 `QuizViewer`。
- `QuizViewer`：
  - 本地维护 `answers: Record<questionId, string>`；
  - 选择/短答/开放题分别用选项按钮与文本域；
  - 「提交」调用 `submitQuizSet`；
  - `status=ungraded` → 展示原因，保留作答可修改重交；
  - 已提交 → 只读展示每题 `learner_answer` / 判定 / 正确答案 / 解析，以及总分。
- 历史恢复：`messages.data.quiz_set` 直接渲染 `QuizViewer`；`status=open` 时再调 `getQuizSet`
  确认最新状态（避免会话写入失败导致的旧快照）。

## 5. 答案边界

前端在提交前只使用公开字段；`correct_answer` / `explanation` 仅在提交后的响应或已提交的
`GET` 投影中出现。前端不做任何答案推断。

## 6. 风险与取舍

| 风险 | 处理 |
|---|---|
| 事件乱序 / 重复 | 按 `set_id` 聚合，`question_id` 去重 |
| 刷新丢题卷 | `messages.data.quiz_set` + `getQuizSet` 双保险 |
| `ungraded` 后作答丢失 | 保留本地 `answers`，仅展示提示 |
| 旧会话无 `data.quiz_set` | 不影响既有渲染分支 |

## 7. 测试计划

- `npm run typecheck`、`npm run build`。
- browser_use：能力芯片、生成事件渲染、提交、复盘、刷新恢复。
- 真实模型 E2E：使用项目已存 API Key，走完 outline → study（四种判分结果）→ 题卷 → review → 完成。
