# Proposal: 会话连续性与答题入口统一

## Why

真实对话暴露三个问题，其中第一个是 `0041` 引入的回归：

1. **运行时证据被模型复述**。`0041` 把 `data.card` / `data.grade` 还原成文本拼进 assistant
   历史正文，模型开始把 `[Question card delivered to the learner] question_id: 9c1e40d7…`
   当成自己的话复述给学习者。题号泄漏到对话里，而真正的 `question_card` 事件没有发出，
   卡片没有选项，学习者只能在输入框里打字。
2. **答题入口分裂**。选择题点卡片，简答题在卡片内的另一个输入框，底部输入框却只能当普通对话；
   一旦卡片没渲染出来，学习者在底部输入框作答就不会走 `mastery_answer`，判分链路断开。
3. **会话中途截断**。会话页用 `search.get("session") || Math.random()` 取会话 id，且从不写回地址栏；
   关掉再打开就是新的空会话，数据库里的旧对话仍在，页面却只显示路径级 `pending_question` 那一道题。

## What Changes

- 题卡与判分证据**不再进入 assistant 正文**：`historyMessages` 只回放真实对话；证据改由
  `recapContext()` 注入到 turn 的运行时上下文（与 `mastery_status` 快照同一通道），并显式声明
  「运行时数据、不得复述、不得向学习者暴露 question_id」。
- 题卡不再自带自由文本输入框：简答题/开放题统一在底部输入框作答；只要存在 `pending_question`，
  底部输入框发送的内容即作为该题作答（`mastery_answer`）。选择题仍可点选项。
- 会话页在无 `?session=` 时自动接上该路径最近一次会话（`listSessions` 首条，按 `updated_at` 降序），
  并把 `session_id` 写回地址栏；会话列表每条「打开」也带自己的 `session_id`。
- 刷新/重进会话时按消息顺序完整渲染历史，一条消息同时含文字与题卡/判分时拆成两段显示（文字在前）。

## Capabilities

### Modified Capabilities

- `agent-turn`：题卡/判分证据改走运行时上下文，不进 assistant 正文；新增会话连续性与答题入口契约。

## Impact

- Java：`TurnRunner`（`historyMessages` / `recapContext` / 上下文拼装）。
- 前端：`sessions/page.tsx`、`QuestionCard.tsx`、`SessionCamp.tsx`、`i18n/{zh,en}.json`。
- 测试：`TurnRunnerTest`（历史不含证据、上下文含回顾）；前端 `typecheck` + `build`。
- 行为：模型不再复述题号；简答题在底部输入框作答即判分；会话可完整恢复。
