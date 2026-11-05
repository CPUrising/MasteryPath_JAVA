# Proposal: 回合循环与工具契约

## Why

`0015` 把模型访问收进了 `adapter/llm`，但还没有「回合」——没有东西把
「系统提示词 + 历史 + 工具定义」送给模型，也没有东西执行模型请求的工具。

参考实现 `agents/loop.py`（162 行）定义了导师的全部编排语义：

1. **系统提示词按模式框定**：`Current mode: {mode}. {session_frame(mode)}`。
2. **每回合挂载全部工具**，门控在**调用期**由工具自己执行（`ADR-005`）——
   因此回合中途切模式，下一次工具调用立即生效，无需重新挂载 schema。
3. **回合预算**（`INV-60`）：单回合工具调用超过 6 次即以 `reason=budget` 结束。
4. **出题即结束回合**（`INV-44`）：工具通过 `ctx.end_turn()` 通知循环，
   循环以 `reason=card` 结束，学习者的作答作为下一条消息到达。
5. **工具永不杀死回合**：坏参数、未知工具、工具抛异常，都转成 `ok=false` 的结果回给模型。

没有这一层，`0017` 的 19 个工具无处挂载，`0019` 的 WS 回合也无从驱动。

## What Changes

- 新增 `adapter/tool` 包：
  - `ToolContext`：可变上下文（路径、模式、会话、回合、`endTurn` 回调、绑定回调、知识库）
  - `ToolResult`：`ok` / `content` / `metadata`
  - `Tool`：`name()` / `description()` / `parameters()` / `execute(params, ctx)`
  - `ToolDefinitions`：把工具表转成 OpenAI 工具定义（按名字升序）
- 新增 `adapter/loop` 包：
  - `StreamEvent`：`type` + `data`
  - `AgentLoop`：`run` / `stream`，含系统提示词、回合循环、预算、事件流

## Capabilities

### New Capabilities

无新增能力；实现 `specs/agent-turn` 的回合编排（该规范未单列循环章节，
行为以参考实现 `agents/loop.py` 为唯一依据）。

### Modified Capabilities

无。`skip_specs: true`：本 change 补齐参考实现已有、而 `0001`–`0015` 尚未实现的能力。

## Impact

- 影响文件：
  - `src/main/java/com/masterypath/adapter/tool/*`（新增 4 个类）
  - `src/main/java/com/masterypath/adapter/loop/*`（新增 2 个类）
  - `src/test/java/com/masterypath/adapter/loop/AgentLoopTest.java`（新增）
  - `scripts/verify.ps1`（`-Scope unit` 补 `*AgentLoopTest`）
- 影响测试：既有 535 个用例**不修改**。
- 明确不做（Out of scope）：
  - 19 个 `Mastery*Tool` 与 `ToolSchemas` → 属 `0017`。
  - `test_loop.py` 里 2 个依赖真实工具的用例
    （`test_loop_builds_outline_then_finishes`、`test_loop_refuses_tool_in_wrong_mode`）
    → 属 `0017`，因为它们的断言对象是 `mastery_build` 与门控文案。
  - `TurnRunner` / WS 回合 → 属 `0019`。
  - `ChatService` → 属 `0019`。
