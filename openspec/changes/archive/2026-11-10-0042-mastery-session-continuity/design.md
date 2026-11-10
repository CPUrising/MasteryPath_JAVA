# Design: 会话连续性与答题入口统一

## 1. 证据通道：运行时回顾，而不是伪造对话

`0041` 把证据拼进 assistant 的 `content`，模型把它当上文复述。修正为两条独立通道：

| 通道 | 内容 | 是否会被模型复述 |
|---|---|---|
| `messages`（历史） | 只含真实 user / assistant 正文 | 是，属正常对话 |
| `context`（运行时） | `mastery_status` 快照 + 会话回顾 | 否，明确标注为运行时数据 |

`TurnRunner.recapContext()` 生成：

```
[Session recap — runtime data, never quote or restate this block]
The runtime already put each question on the learner's card. A card listed as graded is
finished: do NOT re-ask it. A short letter or phrase from the learner refers to the most
recent card.

- card 3f2a… (choice, graded correct, mastery_after 0.55): <prompt> | options: A) … B) …
- card 9c1e… (short, awaiting answer): <prompt>
```

- 只取最近 6 条带 `data.card` 的 assistant 消息，按时间正序。
- 判分结果按 `data.grade.question_id` 与卡片配对；配不上的卡片标 `graded` 但结果未知。
- 题号只出现在运行时上下文，`TurnRunnerTest` 断言它 MUST NOT 出现在发给模型的历史正文里。

`historyMessages` 恢复为「只回放 role + content」，但保留「空正文且无卡片的消息跳过」。

## 2. 答题入口

`QuestionCard` 不再渲染 textarea：

- 有选项：渲染可点选项。
- 无选项：渲染提示 `q.answerBelow`（「在下方输入框作答」）。
- `answered` 时仍显示 `q.answered`；`onSkip` 不变。

会话页的 `send` 在存在 `pending_question` 时自动附加 `mastery_answer`：

```ts
const pending = state?.pending_question;
await runTurn({ ..., message: text,
  mastery_answer: pending ? { question_id: pending.question_id, text } : undefined });
```

选择题点选项走原来的 `answerQuestion`，两条路径共用同一个 `send`。

## 3. 会话恢复

```
进入 /learning/mastery/{pathId}/sessions
  有 ?session=      → 直接加载该会话
  没有 ?session=    → GET /learning/paths/{pathId}/sessions
                       有会话 → 取第一条（updated_at DESC）→ router.replace(?session=id)
                       无会话 → 生成新 id → router.replace(?session=id)
```

`SessionCamp` 的「打开」改为 `masterySessionRoute(pathId, session.mode, session.session_id)`。
`router.replace` 只改查询串，不触发重新挂载，因此不会重复加载。

## 4. 历史渲染

加载时把一条消息拆成最多两段：

```
message.content 非空            → { role, text }
message.data.card               → { role, card }
message.data.grade              → { role, grade }   （legacy：REST 判分消息 content="correct"）
message.data.quiz_set           → { role, quiz }
```

顺序固定为「正文在前，卡片/判分在后」，与实时回合的观感一致。不再过滤空正文的 assistant 消息
以外的任何内容，也不做条数截断（服务端 `getSessionDetail` 返回全部消息）。

## 5. 兼容与风险

- `0041` 写入的 `data.grade` 保留——历史渲染需要它；只是不再进模型正文。
- 有 `pending_question` 时底部输入框一律按作答提交：学习者若想提问，需先「跳过」。
- 旧会话的 `data.grade` 里 `mastery_after` 可能为 0（例如 k3 期初），回顾只如实呈现。
- LLM 上下文仍保留 20 条消息上限；界面显示不受此限制。

## 6. 测试

- `TurnRunnerTest#historyKeepsRuntimeEvidenceOutOfAssistantText`：历史正文 MUST NOT 含
  `[Question card delivered` / `question_id`；`system` 上下文 MUST 含 `[Session recap`。
- 删除 `0041` 的 `historyReplaysCardAndGradeAsText`（语义已反转）。
- 前端：`npm run typecheck`、`npm run build`。
