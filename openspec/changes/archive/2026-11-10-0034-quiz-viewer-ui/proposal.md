# Proposal: 练习题卷前端（QuizViewer）

## Why

`0033` 已完成后端 `deep_question` 回合与 `quiz_sets` / `quiz_items` 存储，但前端没有任何入口与展示：
用户在掌握之路会话里既无法发起“给我一套练习题”，也无法渲染 `quiz_plan` / `quiz_question` / `quiz_set`
事件，刷新后 `messages.data.quiz_set` 也不会恢复。`0034` 补齐前端消费层，让这套能力在 UI 上可用。

## What Changes

- 前端回合请求支持 `capability`（`mastery_path` / `deep_question`）、`num_questions`、`difficulty`。
- 掌握之路会话页增加「练习题」能力入口：选中后下一次发送走 `deep_question`，默认 3 题。
- 消费新事件：`quiz_plan`、`quiz_question`、`quiz_set`，在消息流内联渲染题卷。
- 新增 `QuizViewer`：逐题作答、整卷提交、提交后展示逐题判定/正确答案/解析与总分。
- 新增 `getQuizSet` / `submitQuizSet` REST 客户端；提交前只使用公开字段（无答案）。
- 历史恢复：`messages.data.quiz_set` 恢复题卷；已提交题卷从 `GET` 拉取答案复盘。
- `ungraded` 提交结果在前端提示并保留作答，允许修改后重交。

## Capabilities

### New Capabilities

- `quiz-viewer`: 前端题卷入口、事件渲染、整卷提交与复盘。

### Modified Capabilities

- 无后端行为变化；`deep_question` 契约沿用 `0033`。

## Impact

- 前端：`lib/types.ts`、`lib/ws.ts`、`lib/api.ts`、`components/learning/QuizViewer.tsx`、
  `app/(workspace)/learning/mastery/[pathId]/sessions/page.tsx`、`i18n/{zh,en}.json`。
- 后端：无代码改动（仅在 E2E 验证中真实调用）。
- 测试：`npm run typecheck`、`npm run build`、browser_use 手测、真实模型 E2E（走完一条掌握之路）。
