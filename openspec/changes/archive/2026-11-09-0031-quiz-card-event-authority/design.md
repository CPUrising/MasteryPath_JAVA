# Design: 题卡事件权威化与作答反馈落库

## 1. 现状与缺口

| 层 | 现状 | 缺口 |
| --- | --- | --- |
| 后端事件 | `TurnRunner` 从 `tool_result.metadata.question_card` 派生 `question_card` | 事件没有 `card_kind` / `turn_id` / `objective`，前端无法区分题卡类型 |
| 后端持久化 | `recordTurn` 把题卡写入 assistant `data.card` | 判分反馈没有落库，刷新即丢（`0028` 已知限制） |
| 前端 | 只消费 `pending_question` | 不消费 `question_card`，历史消息也不恢复 `data.card` |
| 判分 | `ungraded` 被 `StudyController` 映射为 422 | 学习者看到“失败”，无法区分“没听懂”和“答错” |
| 短答题 | 只靠 `expected_answer` + 精确/模糊匹配 | 同义表述容易误判；模型常不提供 `accepted_answers` |

## 2. Goals / Non-Goals

**Goals**

1. 题卡事件与消息历史成为同一份公开投影，前端不再依赖 `pending_question` 才能显示题卡。
2. 判分结果（含 `ungraded`）写入会话消息，刷新后仍可见；重复判分不重复写。
3. short 题在注册阶段要求 `accepted_answers`，降低同义表述误判。
4. 保持既有 7 键投影、单槽位、判分算法、掌握门槛、表结构不变。

**Non-Goals**

1. 不实现生成型题卷（`0032/0033`）。
2. 不把 `ungraded` 改成 200；REST 契约保持 422，由前端识别并展示为“未判定”。
3. 不引入 LLM 判分或 LLM 归一化。
4. 不改 `chat` SSE、SM-2、模式门控。

## 3. Decisions

### 3.1 事件字段加在 `StudyTools.questionCard`

`card_kind` 固定为 `"mastery"`；`turn_id` 取 `ctx.turnId()`；`objective` 由知识点 id/name 构造。
`QuestionCardProjector` 的 7 键白名单保持不变，新增字段只加在事件/消息的公共卡片外层。

### 3.2 判分反馈在 `StudyController` 落库，不在工具层

`mastery_grade` 既可能由 REST 题卡调用，也可能由 WS 回合里的模型调用。若在工具层落库，
WS 路径会与 `recordTurn` 重复写消息。因此只在 `StudyController.grade` 成功后落库：

```
StudyController.grade
  → tool.execute(...)
  → result.metadata() 取出 outcome
  → status == "already_graded" ? 跳过
  → learningService.recordGradeInteraction(pathId, sessionId, answer, outcome, turnId)
  → !result.ok() ? throw ApiException(422)
```

`recordGradeInteraction` 写两条消息（user 答案 + assistant `data.grade`），`message_count + 2`，
`last_message` 取 `result` 或答案，`has_pending_question=false`。`session_id` 为空时跳过落库。

### 3.3 `ungraded` 仍返回 422

参考契约与现有测试依赖 422；本次不改状态码。`StudyController` 在抛 422 前仍写入
`data.grade{status=ungraded,reason}`，前端在 `catch` 中识别 `ungraded` 并显示可重答提示。
`Grading` 与 `INV-46` 的零状态变更不变。

### 3.4 short 题的可接受答案校验放在 `StudyTools.Quiz`

`QuizSpec` 仍是纯校验；`StudyTools.Quiz` 在 `normalize` 之后检查：

- `question_type == "short"`；
- `accepted_answers` 为空；
- `expected_answer` 不是纯数值、也不是单 token（无空白）；
- → `ToolResult.failure("short questions need accepted_answers ...")`。

纯数值/单 token 豁免，避免“42”“HTTP”这类答案被无谓拒绝。`open` 不在此检查内。

### 3.5 前端数据模型

`Item` 增加 `card?: PendingQuestion` 与 `grade?: GradeOutcome`：

- 历史消息：保留 `content` 为空但 `data.card` / `data.grade` 存在的消息；
- 同一 `question_id` 只渲染一张卡；已存在 `data.grade` 时卡片标记为已作答；
- 实时 `question_card` 事件追加卡片项；`pending_question` 只在历史没有该卡时兜底；
- `answerQuestion` 追加用户答案气泡与助手反馈气泡；`ungraded` 时保留输入区允许重答。

`QuestionCard` 增加 `answered?: boolean`，已作答时隐藏输入区/选项点击，只保留题干与“已作答”。

### 3.6 兼容性

- 事件新增字段是 additive；旧前端忽略即可。
- `grade` 的 `session_id` / `turn_id` 可选；不传时行为与现在一致（判分但不落库）。
- `messages.data` 新增 `grade` 键；旧消息无该键，读取端按“无判分”处理。
- 不新增表，不改 DDL。

## 4. Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| `already_graded` 重复落库 | 会话出现重复反馈 | 控制器按 `status` 跳过 |
| WS 内模型调用 `mastery_grade` 被重复写 | 消息翻倍 | 只在 REST 控制器落库 |
| `ungraded` 422 被前端当失败 | 学习者困惑 | 前端识别 `status=ungraded` 并显示重答提示 |
| short 强制 accepted_answers 过严 | 模型多一次重试 | 数值/单 token 豁免；错误文案给出补充示例 |
| 历史消息过滤条件改错 | 空正文的判分消息丢失 | 过滤条件改为 `content || data.card || data.grade` |
| 题卡重复渲染 | 同一题出现两张卡 | 按 `question_id` 去重，历史优先 |

## 5. Migration Plan

1. 后端：`StudyTools.questionCard` 增字段；`StudyController.grade` 落库；`LearningService.recordGradeInteraction`。
2. 后端：`StudyTools.Quiz` short 校验。
3. 前端：类型、历史映射、内联题卡、判分反馈、`ungraded` 重答。
4. 测试：`TurnRunnerTest`、`StudyApiTest`、`QuizSpecTest` / `StatusAndQuizToolsTest`、前端手测清单。
5. `.\scripts\verify.ps1 -Change 0031-quiz-card-event-authority`，回填证据。
