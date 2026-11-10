# 验证报告：0042-mastery-session-continuity

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0042-mastery-session-continuity` |
| 执行时间 | 2026-11-10 16:20–16:40（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 命令 | `.\scripts\verify.ps1 -Scope all -Change 0042-mastery-session-continuity`；`npm run typecheck`；`npm run build` |
| 触发来源 | 真实对话中的题号泄漏、简答题入口分裂、重开会话只剩一道题 |

## 2. 变更摘要

- 题卡/判分证据不再拼进 assistant 历史正文；改由 `TurnRunner.recapContext()` 注入运行时上下文
  （与 `mastery_status` 快照同一通道），抬头声明「运行时数据、不得复述」。
- `QuestionCard` 去掉自由文本输入框；无选项时提示「在下方输入框作答」。
- 会话页存在 `pending_question` 时，底部输入框提交即作为 `mastery_answer`。
- 无 `?session=` 时拉取该路径会话列表，接上 `updated_at` 最新一条并 `router.replace` 写回地址。
- `SessionCamp` 每条「打开」带自己的 `session_id`。
- 刷新/重进按「正文 → 题卡/判分 → 题卷」拆分渲染，完整显示且不再被卡片覆盖。

## 3. 问题证据（变更前）

真实对话片段（模型复述运行时数据，题号出现在聊天里）：

```
[Question card delivered to the learner] question_id: 9c1e40d7a5b34f6f8d0e7a2b6c41f3d8
question_type: choice prompt: 你在 mysql> 中执行完 source /tmp/example.sql …
[That card was graded] question_id: 9c1e40d7… learner_answer: B result: correct mastery_after: 0.0
```

根因：`0041` 把证据拼进 assistant 的 `content`，模型把它当成自己的上文复述；真正的
`question_card` 事件没有发出，卡片没有选项，学习者只能改在输入框作答。

会话截断：会话页 `search.get("session") || Math.random()` 取 id 且不写回地址，
关掉再进即新的空会话，只剩路径级 `pending_question` 那一道题。

## 4. 验证结果

```
[INFO] Results:
[INFO] Tests run: 945, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

[PASS] 测试通过。
```

```
> tsc --noEmit            # 通过
> next build              # 通过，11 个路由生成（会话页 7.51 kB）
```

| 用例 | 覆盖 |
|---|---|
| `TurnRunnerTest#historyKeepsRuntimeEvidenceOutOfAssistantText`（新增，替换 0041 反向用例） | 非 system 消息 MUST NOT 含 `[Question card delivered` / `[That card was graded` / `q-card`；system 上下文 MUST 含 `[Session recap` 与卡片/判分摘要 |
| `TurnRunnerTest#gradesCardAnswerBeforeTheTutorSpeaks`（保留） | 判分仍写入 assistant 消息 `data.grade` |

## 5. 行为变化

| 场景 | 变更前 | 变更后 |
|---|---|---|
| assistant 历史正文 | 夹带 `[Question card delivered …] question_id: …` | 只含真实对话；证据进 system 上下文 |
| 简答题作答 | 卡片内独立输入框 | 底部输入框，提交即 `mastery_answer` |
| 会话页无 `?session=` | 随机新会话，历史为空 | 接最近一次会话并写回 URL |
| 会话列表「打开」 | 不带 session id，必开新会话 | 带各自 `session_id` |
| 刷新后的历史 | 带卡片/判分的消息只剩卡片，正文丢失 | 正文、题卡、判分分开依次显示 |

## 6. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 未重跑真实模型 E2E | 中 | 泄漏点是确定性的（历史正文构造），单测已锁死；如需端到端可跑 `.\scripts\e2e-mastery.ps1` |
| D2 | 有 `pending_question` 时底部输入框一律按作答提交 | 低 | 学习者若想提问需先「跳过」；按人类确认的方案执行 |
| D3 | LLM 上下文仍保留 20 条消息上限 | 低 | 界面显示不受限；回顾段覆盖卡片与判分 |
| D4 | 旧会话的 `data.grade.mastery_after` 可能为 0 | 低 | 如实呈现；新回合起为真实值 |

## 7. 结论

- [x] 建议归档（等待人类确认）
- [ ] 需修复后复验
- [ ] 需人类决策
