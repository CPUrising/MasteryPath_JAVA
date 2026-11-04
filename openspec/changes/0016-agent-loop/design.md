# Design: 回合循环与工具契约

## Context

参考实现 `agents/loop.py`（162 行）与 `capabilities/mastery/tools.py` 的契约部分
（`ToolContext` / `ToolResult` / `tool_definitions`，约 60 行）。

Java 侧 `0015` 已提供 `LlmClient` / `StreamChunk` / `ToolCall` / `FakeLlmClient`；
`0005` 已提供 `SessionMode`（含 `instruction()` 提示词块）与 `BehaviorBudget`。
本 change 只写编排，不碰服务层。

## Goals / Non-Goals

**Goals**

1. 系统提示词逐字复刻（含 `Current mode: {mode}.` 与模式提示词块）。
2. 回合循环的 5 种结束原因：`done` / `budget` / `card` / `max_rounds`，以及工具失败不终止。
3. 事件顺序：`text_delta` → `tool_call` → `tool_result` → … → `turn_end`。
4. OpenAI 工具历史的写法：assistant 的 `tool_calls` 消息必须在 tool 结果之前。
5. 预算判定在**执行工具之前**（第 7 次调用直接结束，不执行）。

**Non-Goals**

1. 不做 19 个工具（`0017`）。
2. 不做 `TurnRunner` / WS（`0019`）。
3. 不做 `ChatService`（`0019`）。

## Decisions

1. **`ToolContext` 用可变类而非 record**
   - 参考实现是 `@dataclass`，字段可写：`session_mode`、`path_id`、`mode_changed`、
     `path_changed`、`end_turn` 都会被工具改写。
   - Java 用普通类 + getter/setter，`endTurn` 是 `Runnable`。

2. **`Tool` 接口的 `parameters()` 返回 `Map<String, Object>`**
   - 参考实现的 schema 是手写 dict（`TOOL_PARAMETERS`），不是从方法签名推导。
   - 接口带 `parameters()` 使 `0017` 的每个工具自带 schema，`ToolDefinitions` 只做组装。

3. **`ToolDefinitions.of(tools)` 按名字升序**
   - 参考实现：`for name in sorted(tools)`。
   - 升序让「同一组工具」每次生成的 schema 数组完全一致，便于断言与缓存。

4. **系统提示词的逐字文本**
   ```
   You are the MasteryPath tutor. Decide what to teach and ask; a deterministic
   engine decides whether the learner may advance. Current mode: {mode}. {frame}
   Begin each turn with the mastery_status snapshot supplied by the runtime, or
   call mastery_status first; never guess a knowledge_point_id or the next
   objective. Trust the engine's next objective and use mastery_mode when the
   learner asks for something this mode cannot do.
   ```
   - `{mode}` 用 `SessionMode.normalize(...).value()`（未记录模式 → `study`）。
   - `{frame}` 用 `SessionMode.instruction()`。
   - `extraContext` 非空时追加 `"\n\n" + extraContext`。

5. **`maxRounds` 默认 6**
   - 参考实现 `AgentLoop(tools, *, max_rounds=6)`。
   - 循环体是 `for _ in range(max_rounds)`，因此最多 6 轮模型调用。

6. **预算判定在工具执行之前**
   - 参考实现：`call_count += 1; if call_count > MAX: yield turn_end{budget}; return`。
   - 因此第 7 次调用**不会执行**，也不会产出 `tool_call` / `tool_result` 事件。
   - 计数是**跨轮累计**，不是每轮重置。

7. **`endTurn` 在工具执行之后检查**
   - 参考实现：先 `yield tool_result`，再 `if ended: yield turn_end{card}; return`。
   - 因此出题工具的结果**先**到达调用方，回合**再**结束。

8. **工具失败的三种降级**
   ```
   call.parseError        → ok=false, "invalid tool arguments"
   tools.get(name)==null  → ok=false, "unknown tool: {name}"
   execute 抛异常          → ok=false, "tool failed: {message}"
   ```
   - 三者都**不终止回合**，结果照常写进历史，让模型自我纠正。

9. **assistant 消息的 `content` 为空时写 `null`**
   - 参考实现：`"content": round_text or None`。
   - OpenAI 要求「请求工具调用的 assistant 消息」在 tool 结果之前存在；
     `content` 为 `null` 是合法写法。

10. **`streamChatEvents` 缺失时回退到 `chat`**
    - 参考实现用 `getattr(client, "stream_chat_events", None)` 探测。
    - Java 侧 `LlmClient` 有默认实现，因此**永远存在**；但为保持「整段一次产出」的
      语义，`AgentLoop` 仍按 chunk 消费，不区分来源。

11. **`run` 不暴露 `enforce_mode_set`**
    - 参考实现 `run(...)` 有这个参数但**从未使用**（死参数）。
    - Java 侧不移植，避免误导。已登记到 verify 报告偏差表。

12. **`StreamEvent` 用 record**
    - `type` + `data`（`Map<String, Object>`），不可变。
    - 事件类型常量集中在 `StreamEvent` 里，避免字符串散落。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 预算判定放在执行之后 | 第 7 次调用被执行，产生副作用 | 用例断言第 7 次调用**没有** `tool_call` 事件且工具未被调用 |
| 预算计数每轮重置 | 6 轮 × 6 次 = 36 次调用，远超上限 | 用例构造跨轮多次调用 |
| `endTurn` 在 `tool_result` 之前检查 | 调用方收不到出题结果 | 用例断言事件顺序 `tool_result` 在 `turn_end` 之前 |
| assistant 消息缺 `tool_calls` | OpenAI 拒绝后续 tool 消息 | 用例断言第二轮历史里有 assistant + tool 配对 |
| `content` 写空串而非 `null` | 部分供应商报错 | 用例断言空正文时 `content` 为 `null` |
| 工具异常向上抛 | 整回合崩掉 | 用例用抛异常的工具断言 `ok=false` 且回合继续 |
| schema 顺序不稳定 | 断言与缓存失效 | 用例断言按名字升序 |

## Migration Plan

1. `ToolContext` / `ToolResult` / `Tool` / `ToolDefinitions`。
2. `StreamEvent` + `AgentLoop`（`run` / `stream`）。
3. 测试：`AgentLoopTest`（用假工具，不依赖服务层）。
4. `verify.ps1 -Scope unit` → 全量 → `pytest tests/test_loop.py` 对照 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/agents/loop.py` 为唯一依据。
- 参考实现测试空白（本 change 会补）：
  - 预算跨轮累计（参考只测单轮超限）；
  - `endTurn` 与 `tool_result` 的先后；
  - `content` 为 `null` 的 assistant 消息；
  - `ToolDefinitions` 的升序与 schema 完整性；
  - `extraContext` 追加位置。
