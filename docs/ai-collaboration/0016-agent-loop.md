# AI 协作留痕：0016-agent-loop

## 1. prompt 范围

- 授权修改的文件：`src/main/java/com/masterypath/adapter/tool/*`（新增 5 个文件）、
  `src/main/java/com/masterypath/adapter/loop/*`（新增 3 个文件）、
  `src/test/java/com/masterypath/adapter/loop/AgentLoopTest.java`（新增）、
  `scripts/verify.ps1`（`-Scope unit` 补 `*AgentLoopTest`）、
  `openspec/changes/0016-agent-loop/**`、`docs/**`。
- 明确不做的事：23 个工具（19 mastery + 4 导航）与 schema 表（`0017`）、`TurnRunner` / WS（`0019`）、
  `ChatService`（`0019`）。
- 出处：用户指令原文「提交后继续0016」；`docs/migration-plan.md` §5 第 16 行（拆分后）。

## 2. AI 产出

- 新增文件（`adapter/tool`）：`ToolContext` / `ToolResult` / `Tool` / `ToolDefinitions` / `package-info`
- 新增文件（`adapter/loop`）：`StreamEvent` / `AgentLoop` / `package-info`
- 新增测试：`AgentLoopTest`（19）
- 关键决策：
  - **`stream` 必须惰性**：用显式状态机（`START_ROUND` → `CONSUME_CHUNKS` →
    `EXECUTE_CALL` → `AFTER_TOOL_CALL` → `END_CARD` → `FINISHED`），
    使 `text_delta` 在工具执行**之前**到达调用方。参考实现用生成器达到同样效果。
  - **预算判定在工具执行之前**：第 7 次调用**不执行**，也不产出 `tool_call` 事件。
  - **`endTurn` 在 `tool_result` 之后检查**：先交付结果，再以 `card` 结束。
  - **工具失败三降级**：坏参数 / 未知工具 / 抛异常，都 `ok=false` 且不终止回合。
  - **assistant 消息空正文写 `null`**：OpenAI 的合法写法，也是参考实现原样。
  - **`ToolDefinitions` 按名字升序**：让同一组工具每次生成的 schema 数组完全一致。
  - **`enforce_mode_set` 不移植**：参考实现该参数从未使用（死参数）。

## 3. 人工修正点

- AI 自我修正 2 轮：
  1. **`stream` 初稿返回 `List`**：无法验证「`text_delta` 先于工具执行」。
     改为显式状态机 + `Iterator`，`#textArrivesBeforeToolRuns` 才成立。
  2. **`endTurn` 分支漏了 `turn_end{card}`**：初稿只返回 `tool_result` 就结束，
     调用方永远收不到 `card` 原因。新增 `END_CARD` 状态修正。
  3. **预算测试写错**：初稿每轮 1 次调用，`maxRounds=6` 会先触发 `max_rounds`，
     永远到不了第 7 次。改为单轮 7 次调用（参考实现同样如此）。

## 4. 回归结果

- 用例数 / 失败数：本 change 新增 19 / 0；全量 554 / 0（535 → 554）。
- 证据链接：[`docs/verify/0016-agent-loop.md`](../verify/0016-agent-loop.md)
- 日志：`artifacts/verify-0016-agent-loop-20261104-201111.log`
- 参考实现对照：在 `../MasteryPath` 执行 `pytest tests/test_loop.py -q` → **10 passed**
  （该文件共 12 个用例，2 个依赖真实工具，归 `0017`）。

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| `stream` 是否惰性 | **必须惰性** | 参考实现是生成器；`test_loop_streams_text_before_running_a_tool` 断言工具尚未执行 |
| 预算判定在执行前还是后 | **执行前** | 参考实现 `call_count += 1; if > MAX: return` 在 `tool.execute` 之前 |
| 预算计数是否每轮重置 | **不重置** | 参考实现 `call_count` 在 `for` 循环之外 |
| `endTurn` 与 `tool_result` 的先后 | **先 `tool_result`** | 参考实现先 `yield tool_result` 再检查 `ended` |
| 工具异常是否向上抛 | **不抛，转 `ok=false`** | 参考实现 `except Exception` 兜住 |
| assistant 空正文写什么 | **`null`** | 参考实现 `"content": round_text or None` |
| schema 顺序 | **按名字升序** | 参考实现 `for name in sorted(tools)` |
| `enforce_mode_set` 是否移植 | **不移植** | 参考实现从未使用该参数 |
| `ToolContext.service` 类型 | **暂用 `Object`** | 本 change 不依赖服务层；`0017` 收窄为 `LearningService` |
