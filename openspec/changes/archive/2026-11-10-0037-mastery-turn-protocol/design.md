# Design: 题卡与回合协议对齐 DeepTutor

## 1. 请求字段

```json
{
  "session_id": "...", "path_id": "...", "mode": "study",
  "message": "B",
  "mastery_answer": {"question_id": "...", "text": "B"},
  "mastery_skip": {"question_id": "..."}
}
```

`mastery_answer` 与 `mastery_skip` 互斥；二者都缺省时按普通消息处理。
若 `message` 为空而 `mastery_answer.text` 非空，用答案文本作为本轮用户消息。

## 2. 回合顺序

```
[mastery_grade tool_result]?   ← 作答先行判分（best-effort）
[mastery_skip tool_result]?    ← 跳过先行清理
session_meta? → text_delta... → tool_call/tool_result... → turn_end
```

- 判分调用 `mastery_grade` 工具（与模型调用同一入口），失败不拖垮回合。
- `tool_result` 形状：`{name, ok, content, metadata:{mastery_grade: {...}}}`。
- 判分结果字段在 `LearningService.grade` 基础上补 `correct_label` / `correct_body` /
  `learner_answer` / `independent`。
- 随后 `mastery_status` 快照已是「无待答题」，导师直接进入反馈与下一步。

## 3. 历史注入

`TurnRunner` 从 `getSessionDetail(sessionId)` 读取此前消息，按 `role` + `content`
映射为 LLM messages（跳过空正文，最多 20 条），再接当前 user 消息。
这使导师能看到上一轮讲解与反馈，而不是每轮从零开始。

## 4. 选项乱序

`StudyTools.Quiz` 在 `QuizSpec.normalize` 之后、注册之前：

1. 找到 `expected_answer` 对应的选项正文；
2. `Collections.shuffle`；
3. 按位置重贴 `A/B/C...`；
4. `expected_answer` 改为正确正文的新标签。

卡片与判分共享同一份持久化选项，因此标签始终一致。

## 5. 工具参数

- `mastery_grade`：`answer` 必填；`question_id` 可选，缺省取当前待答题。
- `mastery_skip_question`：无参数，缺省跳过当前待答题。
- `mastery_grade` 返回不再有 `ungraded` 分支（0036 已移除该结果）。

## 6. 前端

- `answerQuestion` → `runTurn({message: answer, mastery_answer:{...}})`。
- `onSkip` → `runTurn({message:"跳过这道题", mastery_skip:{...}})`。
- 消费 `tool_result.metadata.mastery_grade`，追加判定气泡并把题卡标为已答。
- 不再调用 `api.grade`；REST 端点暂时保留。

## 7. 测试

- `TurnRunnerTest`：作答回合先发 `mastery_grade` tool_result，且 LLM 消息含历史。
- `WsTurnTest`：`mastery_answer` 解析并判分。
- `StudyToolsTest`：选项乱序后正确正文标签与 `expected_answer` 一致；题卡含 `allow_free_text`；
  `mastery_grade` 缺省题号、`mastery_skip_question` 无参可用。
