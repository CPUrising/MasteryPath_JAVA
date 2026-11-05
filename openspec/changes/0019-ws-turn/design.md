# Design: WS 回合

## Context

参考实现 `agents/turn_runner.py`（332 行）分五块：

| 块 | 行号 | 内容 |
| --- | --- | --- |
| 数据结构 | 41–56 | `TurnRequest` / `TurnEvent` |
| 分派 | 78–86 | `capability == "mastery_path"` → `_run_mastery`，否则走 `ChatService` |
| 解析辅助 | 89–153 | `_session_mode` / `_resolve_path_id` / `_knowledge_base_ids` / `_source_index` |
| mastery 回合 | 155–275 | 上下文装配、快照注入、`AgentLoop` 驱动、`question_card` 派生、`session_meta`、错误映射 |
| 持久化 | 277–329 | `_finish_interrupted_turn` / `_persist_mastery_turn` |

`api/app.py:555-600` 的 WS 端点只做三件事：收一条 JSON、把 `TurnRunner` 的事件流
逐条 `send_json({"type", "data"})`、关闭连接。

Java 侧 `0016` 已有 `AgentLoop`（惰性 `Iterator<StreamEvent>`），`0017` 已有 23 个工具与
`mountedTools(ctx)`，`0018` 已有 `CorsConfig`。本 change 只写编排层与 WS 端点。

## Goals / Non-Goals

**Goals**

1. 事件名与顺序逐字一致：`text_delta` / `tool_call` / `tool_result` / `question_card` /
   `turn_end` / `session_meta` / `error`。
2. `question_card` 从 `tool_result.metadata.mastery_question_card` 派生（不是新事件源）。
3. 错误永不关闭 socket：`LlmError` → `error{error_code, message, retryable}`，
   其他异常 → `error{error_code: "internal_error"}`。
4. 部分文本保留：流中断时已产出的 `text_delta` 必须落库。
5. `session_meta` 只在模式或路径**真的变了**时发。

**Non-Goals**

1. 不做普通聊天（`ChatService`）→ `0020`。
2. 不做知识库检索（`knowledge_context`）→ `0021`。
3. 不做设置解析（`resolve_llm_config`）→ `0022`。
4. 不做 SSE 端点（`/chat/sessions/{id}/turns`）→ `0020`。

## Decisions

1. **`TurnRunner` 只做 mastery，chat 分支留接口**
   - 参考实现 `run()` 按 `capability` 分派；Java 侧本 change 只实现 `mastery_path`，
     `chat` 分支抛 `UnsupportedOperationException` 并在 `0020` 补齐。
   - 理由：`ChatService` 依赖 `ChatStore`（独立 `chat.db`）与 `model_resolution`，
     属另一种行为增量（`AGENTS.md` §3）。

2. **`ClientBuilder` 是函数式接口，不是 Spring Bean**
   - 参考实现 `client_builder(selection, session_key)` 是闭包，测试直接传 lambda。
   - Java 侧定义为 `@FunctionalInterface ClientBuilder { LlmClient build(Object selection, String sessionKey); }`，
     `TurnRunner` 构造时注入。默认实现（读设置）属 `0022`。

3. **`_resolve_path_id`：会话绑定优先**
   - 参考实现：会话有 `path_id` 就用它，否则用请求里的 `path_id`（都 `strip()`）。
   - 这是 `TC-S5`（回合内切换路径持久化）的前提。

4. **`_session_mode`：先查库，再退回内存表**
   - 参考实现：`store.get_session(sid).mode` 优先；会话不存在时用
     `enforced_mode(session_modes.get(sid) or fallback or None)`。
   - Java 侧模式总是先落库（`0018` 的 `setSessionMode`），因此只需查库；
     但**保留 `fallback` 参数**（请求里的 `mode`），因为首次回合会话可能还不存在。

5. **`mastery_status` 快照注入在 `AgentLoop` 之前**
   - 参考实现：`TOOLS["mastery_status"].execute({}, ctx)`，`ok` 时把
     `[Current-turn mastery_status snapshot]\n...` 拼到 `extra_context` 前面。
   - **异常必须吞掉**（`logger.warning`）：状态读取失败不能拖垮整个回合。
   - 只在 `path_id` 非空时注入。

6. **`question_card` 是派生事件，不是 `AgentLoop` 产出**
   - 参考实现：收到 `tool_result` 后检查 `metadata[QUESTION_CARD_KEY]`，有则**额外**发一条
     `question_card` 事件。
   - 因此事件序列可能是 `tool_result` → `question_card` → `turn_end{card}`。
   - `QUESTION_CARD_KEY = "mastery_question_card"`（`0017` 已定义）。

7. **`session_meta` 在 `turn_end` 之后、持久化之前**
   - 参考实现：`loop.stream` 结束后，若 `mode_changed || path_changed` 则发
     `session_meta{mastery_session_mode, mastery_path_id}`。
   - 错误路径同样发（在 `error` 之前）。
   - **顺序不可调换**：前端靠 `session_meta` 更新侧栏，必须在 `turn_end` 之后到达。

8. **`_persist_mastery_turn` 是 best-effort**
   - 参考实现：`try: record_turn(...) except Exception: pass`。
   - 持久化失败不能让客户端看到错误——回合内容已经流式送达。

9. **`_finish_interrupted_turn` 只在有可见内容时落库**
   - 参考实现：`has_text or has_card` 才 `_persist_mastery_turn`。
   - 理由：纯错误回合（模型立刻失败）不该在会话里留下空消息对。

10. **WS 端点用 `ConcurrentWebSocketSessionDecorator` 或直接 `sendMessage`**
    - 参考实现用后台线程 + 队列，避免阻塞事件循环。
    - Spring 的 `WebSocketHandler` 本身在独立线程池执行，`handleMessage` 内同步迭代
      `TurnRunner.run(...)` 即可；不需要额外线程。
    - 但必须**逐条 flush**：`session.sendMessage(new TextMessage(json))` 每条一次。

11. **WS 消息格式 `{"type": ..., "data": {...}}`**
    - 参考实现：`await websocket.send_json({"type": event.type, "data": event.data})`。
    - 与 REST 的 `{ok, content, ...}` 不同，不可混用。

12. **`recordTurn` 的 `message_count` 是 `+2`**
    - 参考实现：`session["message_count"] = int(session.get("message_count", 0)) + 2`。
    - 一次回合固定写 user + assistant 两条。

13. **`recordTurn` 的 `last_message` 取 assistant 优先**
    - 参考实现：`(assistant_text or user_text).strip()[:200]`。
    - 注意是 Python 真值语义：空字符串回退到 user。

14. **`recordTurn` 的 `has_pending_question` 由 `card` 决定**
    - 参考实现：`bool(card)`。
    - 有卡片 → `true`，下一回合 `mastery_sessions` 会显示「等作答」。

15. **`ensureSession` 已存在时原样返回**
    - 参考实现：`if existing is not None: return existing`。
    - **不改 mode**：`recordTurn` 之后单独 `session["mode"] = mode`。

16. **`_source_index` 本 change 只做文件来源**
    - 参考实现：知识库来源走 `knowledge_service.store.collect_documents`，
      文件来源走 `read_text(metadata["path"])`。
    - 知识库分支属 `0021`；本 change 只处理 `excerpt` 与文件读取。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| `question_card` 未派生 | 前端拿不到题卡，无法作答 | 用例断言 `tool_result` 后紧跟 `question_card` |
| `session_meta` 在 `turn_end` 之前发 | 前端侧栏状态错乱 | 用例断言事件顺序 |
| 快照注入异常未吞 | 状态读取失败导致整回合失败 | 用例用会抛异常的 service 断言仍产出 `text_delta` |
| 流中断未落库 | 用户看到文本但刷新后消失 | 用例断言 `messages` 有 assistant 行 |
| 纯错误回合也落库 | 会话里出现空消息对 | 用例断言 `messages` 为空 |
| `recordTurn` 的 `message_count` 写成 `+1` | 会话列表计数错 | 用例断言 `== 2` |
| WS 端点阻塞 | 长回合期间 keepalive 超时 | Spring 的 WS 线程池已隔离；用例断言多帧到达 |
| `chat` 分支未实现 | 前端普通聊天 500 | 明确抛 `UnsupportedOperationException`，`0020` 补齐 |

## Migration Plan

1. 服务层 2 个缺口（`ensureSession` / `recordTurn`）。
2. `adapter/llm/ClientBuilder`（接口 + 默认实现）。
3. `adapter/turn/TurnRequest` + `TurnEvent`。
4. `adapter/turn/TurnRunner`（解析辅助 + mastery 回合 + 持久化）。
5. `adapter/web/WsTurnHandler` + `WebSocketConfig`。
6. 测试：`TurnRunnerTest` + `WsTurnTest`。
7. `verify.ps1 -Scope api` → 全量 → `pytest test_turn_runner.py test_ws_runtime.py` 对照 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/agents/turn_runner.py` 为唯一依据。
- `test_ws_runtime.py` 只断言「装了 WS 实现」，Java 侧对应「`spring-boot-starter-websocket` 在依赖里」，
  由 `WsTurnTest` 的端到端用例间接证明。
