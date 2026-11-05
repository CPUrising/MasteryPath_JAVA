# Tasks

## 1. 工具契约

- [x] 1.1 `ToolContext`：`pathId` / `sessionMode` / `sessionId` / `turnId` / `endTurn` / `bindMode` / `bindPath` / `modeChanged` / `pathChanged` / `knowledgeService` / `kbIds` / `sourceIndex`
- [x] 1.2 `ToolResult`（`ok` / `content` / `metadata`）+ `Tool` 接口（`name` / `description` / `parameters` / `execute`）
- [x] 1.3 `ToolDefinitions.of(tools)`：按名字升序，`type=function` + `name` / `description` / `parameters`

## 2. 回合循环

- [x] 2.1 `StreamEvent`（`type` / `data`）+ 事件类型常量
- [x] 2.2 `AgentLoop` 系统提示词：`Current mode: {mode}.` + 模式提示词块 + `extraContext` 追加
- [x] 2.3 `AgentLoop.stream`：逐轮消费 chunk、`text_delta` 即时产出、assistant `tool_calls` 消息、`tool_call` / `tool_result` 事件、`endTurn` → `card`、预算 → `budget`、无调用 → `done`、轮数耗尽 → `max_rounds`
- [x] 2.4 工具失败三降级：坏参数 / 未知工具 / 抛异常，均 `ok=false` 且不终止回合

## 3. 测试与验证

- [x] 3.1 `AgentLoopTest`：文本流、工具执行、事件顺序、预算（跨轮累计）、`endTurn`、`max_rounds`、坏参数、未知工具、抛异常、工具历史、schema 升序、系统提示词（19 用例）
- [x] 3.2 `.\scripts\verify.ps1 -Scope unit -Change 0016-agent-loop`（467 通过）
- [x] 3.3 全量 `.\scripts\verify.ps1 -Change 0016-agent-loop`（554 通过）
- [x] 3.4 与参考实现对照（`pytest tests/test_loop.py -q` → 10 passed；2 个依赖真实工具的用例归 `0017`）
- [x] 3.5 回填 `docs/verify/0016-agent-loop.md`、`docs/regression-log.md`、`docs/traceability.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0016-agent-loop.md`、`README.md`
