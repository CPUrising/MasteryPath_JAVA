# Proposal: WS 回合

## Why

`0018` 把 REST 端点全部落地了，但**掌握式学习的主通道是 WebSocket**：
前端 `/ws` 发一条消息，后端逐条推 `text_delta` / `tool_call` / `tool_result` /
`question_card` / `turn_end` / `session_meta` / `error`。

参考实现 `agents/turn_runner.py`（332 行）是这条通道的全部逻辑，`api/app.py:555-600`
只是把它接到 WS 上。没有这一层，前端无法进行任何一次真正的学习回合。

## What Changes

- 新增 `adapter/turn` 包：
  - `TurnRequest` / `TurnEvent`：回合请求与事件（与参考实现同名同字段）
  - `TurnRunner`：mastery 回合编排——路径解析、模式解析、知识库 id、来源索引、
    `mastery_status` 快照注入、`AgentLoop` 驱动、`question_card` 派生、
    `session_meta` 发射、错误映射、回合持久化
- 新增 `adapter/web/WsTurnHandler`：`/ws` 端点，把 `TurnRunner` 的事件流逐条推给客户端
- 服务层补 2 个缺口：`ensureSession` / `recordTurn`
- 新增 `adapter/llm/ClientBuilder`：把「模型选择 → `LlmClient`」抽成可注入的工厂
  （本 change 只提供接口与默认实现；设置解析属 `0021`）

## Capabilities

### New Capabilities

无新增能力；实现 `specs/agent-turn` 的 WS 契约（行为以参考实现 `turn_runner.py` 为唯一依据）。

### Modified Capabilities

无。`skip_specs: true`：本 change 补齐参考实现已有、而 `0001`–`0018` 尚未实现的能力。

## Impact

- 影响文件：
  - `src/main/java/com/masterypath/adapter/turn/*`（新增 3 个类）
  - `src/main/java/com/masterypath/adapter/web/WsTurnHandler.java`（新增）
  - `src/main/java/com/masterypath/adapter/llm/ClientBuilder.java`（新增）
  - `src/main/java/com/masterypath/service/LearningService.java`（+2 方法）
  - `src/test/java/com/masterypath/adapter/turn/*Test.java`（新增）
  - `scripts/verify.ps1`（`-Scope api` 补 `*TurnRunnerTest`、`*WsTurnTest`）
- 影响测试：既有 675 个用例**不修改**。
- 明确不做（Out of scope）：
  - 普通聊天（`ChatService` / `ChatStore` / `chat_routes` / `test_chat_nav`）→ 属 `0020-chat-service`
  - 知识库检索与 `knowledge_context` → 属 `0021-knowledge-base`
  - 设置解析（`resolve_llm_config`）与提供方探测 → 属 `0022-settings-task-model`
  - 大纲评测 CLI → 属 `0023-outline-eval-cli`
  - 验收 → 属 `0024-acceptance`
