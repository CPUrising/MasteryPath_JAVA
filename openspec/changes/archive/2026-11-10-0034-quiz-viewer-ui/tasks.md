# Tasks

## 1. 类型与 API

- [x] 1.1 `lib/types.ts` 增加 `QuizQuestion` / `QuizSet` / `QuizSetSubmitOutcome`，`SessionMessage.data` 增加 `quiz_set`。
- [x] 1.2 `lib/api.ts` 增加 `getQuizSet` / `submitQuizSet`。
- [x] 1.3 `lib/ws.ts` 的 `TurnRequest` 增加 `capability` / `num_questions` / `difficulty`。

## 2. 题卷组件

- [x] 2.1 新增 `components/learning/QuizViewer.tsx`：作答、提交、ungraded 提示、提交后复盘与总分。
- [x] 2.2 `i18n/zh.json` / `en.json` 增加题卷文案。

## 3. 会话页接入

- [x] 3.1 增加能力芯片（掌握辅导 / 练习题）并透传 `capability` 与 `num_questions`。
- [x] 3.2 消费 `quiz_plan` / `quiz_question` / `quiz_set` 事件并内联渲染。
- [x] 3.3 历史恢复：`messages.data.quiz_set` 渲染，已提交从 `getQuizSet` 拉取复盘。

## 4. 验证

- [x] 4.1 `npm run typecheck` 与 `npm run build` 通过。
- [x] 4.2 browser_use：生成、提交、复盘、刷新恢复。
- [x] 4.3 真实模型 E2E：走完一条掌握之路（outline → study 四种判分结果 → 题卷 → review → 完成），证据写入 `docs/verify/0034-quiz-viewer-ui.md`。
- [x] 4.4 追加 `docs/regression-log.md` 与 `docs/traceability.md`。

## Workflow follow-up

- [ ] 人类审查证据后归档。
