# Design: 普通聊天服务

## Context

参考实现分四块：

| 块 | 行号 | 内容 |
| --- | --- | --- |
| `chat/store.py` | 1–232 | 独立 `chat.db`：`sessions` + `messages`、`_migrate`、CRUD |
| `chat/service.py` | 1–248 | 会话 CRUD 转发、`stream_turn`（无工具 / 导航工具两条分支） |
| `agents/model_resolution.py` | 1–113 | `normalize_selection` / `resolve_llm_config` / `build_client` |
| `api/chat_routes.py` | 1–83 | `/chat/sessions` CRUD + `/turns` 的 SSE |

Java 侧 `0019` 已有 `TurnRunner`（`chat` 分支抛 `UnsupportedOperationException`）、
`AgentLoop`、4 个导航工具、`OpenAiCompatibleClient`。本 change 补存储、服务、解析与传输。

## Goals / Non-Goals

**Goals**

1. `chat.db` 表结构与参考实现 DDL 逐字一致，可直接打开旧库。
2. 事件词汇表与 WS 同构：`text_delta` / `tool_call` / `tool_result` / `turn_end` / `error`
   （**无** `question_card` / `session_meta`）。
3. SSE 帧格式 `data: {"type": ..., "data": {...}}\n\n`。
4. 导航工具在普通聊天里只读可用，`mastery_topics` 的 `metadata` 原样透出。
5. 无模型配置时报 `error{error_code: "bad_request"}`，文案含 `No language model`。

**Non-Goals**

1. 不做知识库检索（`knowledge_context` 恒为空）→ `0021`。
2. 不做提供方探测与 Task Model → `0022`。
3. 不做 mastery 分支的任何改动（`0019` 已冻结）。
4. 不做 `stream_chat` 的「非工具」旧式客户端回退之外的分支。

## Decisions

1. **`SettingsStore` 落盘纳入本 change**
   - `0018` 曾写「落盘属 `0022`」。本 change 必须推翻该划分：`resolve_llm_config` 要读到
     用户配置的 provider/model，否则 chat 回合永远报「未配置」。
   - 提供方探测（`/settings/providers/probe`）与 Task Model **仍属 `0022`**。
   - `SettingsController` 同步改为委托 `SettingsStore`，避免「UI 写入内存、chat 读文件」的双源。

2. **`ChatStore` 用独立 `chat.db`**
   - 与 `learning.db` 同目录、不共库（参考实现如此）。表名相同但列不同，共库会冲突。
   - `INV-52`（一个 workspace 一个库）指学习数据；chat 是另一份数据，仍一 workspace 一份。

3. **`ChatStore.getSession` 抛 `UnknownSession` 而非返回 `null`**
   - 对齐参考实现的 `KeyError`。REST 层 `catch` 后映射 404，服务层用它区分「会话没了」。

4. **`message_count` / `last_message` 只在 `listSessions` 里算**
   - 参考实现 `_session_row` 不含这两个键，`list_sessions` 单独补。`getSession` 不补——
     照抄，否则 `session_detail` 的键集合会多出两个。

5. **`updateSession` 的三条归一不可省**
   - `kb_ids` 非列表时**保留原值**（不是清空）。
   - `preferences` 是**合并**而非替换；非字典的传入被忽略。
   - `preferences.llm_selection.model_id` 回写 legacy `model_id` 列（前端仍读该列）。

6. **`createSession` 的 id 形如 `c` + 12 位 hex**
   - 参考实现 `f"c{uuid.uuid4().hex[:12]}"`。前端靠前缀区分 chat 与 learning 会话。

7. **`streamTurn` 的空消息不落库**
   - 参考实现先校验 `message.strip()`，为空直接 `yield error` 并返回——**在 `add_message` 之前**。
   - 文案 `"Type a message first."`，**不带** `error_code`。

8. **未知会话的文案独立**
   - `"This conversation no longer exists."`，同样不带 `error_code`。

9. **标题填充用 `text[:40]`**
   - 只在 `title` 为空时；`text` 已 strip。

10. **历史只取 `user` / `assistant` 且正文非空**
    - 过滤掉空的 assistant 行（错误回合可能留下），避免模型看到空消息。

11. **system 消息最多三条**
    - 主提示恒有；知识库参考块有才加；附件块有才加。顺序固定。

12. **导航工具回合的判定只看客户端能力**
    - 参考实现是 `nav_tools and learning_service is not None and tool_capable`。
      Java 侧前两者恒成立（4 个导航工具 + `LearningService`），因此等价于「客户端实现了
      `streamChatEvents` 或 `chat`」——而 `LlmClient` 接口两者都有，故**恒走导航分支**。
    - 这一差异不影响可观察行为：无工具调用时 `AgentLoop` 直接 `turn_end{done}`。

13. **导航回合的系统提示 = `_SYSTEM` + 导航说明**
    - 说明段逐字复刻，含「Never pretend to open or teach a topic in this chat」。

14. **`tool_call` / `tool_result` 的数据展平到事件顶层**
    - 参考实现 `yield {"type": event.type, **event.data}`，不是 `{"type", "data"}` 嵌套。
      因此 SSE 帧是 `{"type": "tool_result", "name": ..., "ok": ..., "content": ..., "metadata": ...}`。
    - `text_delta` 只保留 `text`（`**event.data` 里也只有它）。
    - `turn_end` 由服务层自己构造（带 `session_id` / `title`），不是透传 `AgentLoop` 的。

15. **落库时机：流结束或中断时**
    - 正常结束：`answer` 非空才写 assistant。
    - `LlmError` 中断：已收集的 `collected` 非空则写 assistant，再 `yield error`。

16. **`turn_end` 的 `title` 重新读会话**
    - 标题可能在本次回合刚被填充，用回合开始时的快照会给出空串。

17. **SSE 帧格式与 WS 同构**
    - `data: {"type": ..., "data": {...}}\n\n`；响应头加 `Cache-Control: no-cache`、
      `X-Accel-Buffering: no`（防反向代理缓冲）。

18. **`SettingsController` 的归一规则移入 `SettingsStore`**
    - `SettingsApiTest` 的 9 个断言**不修改**——行为等价，只是实现从内存换成文件。

19. **`WebConfig` 的 `TurnRunner` 改用 `ModelResolution` 构造 `ClientBuilder`**
    - `0019` 的占位实现直接抛「未配置模型」；现在改为 `resolveLlmConfig(selection)` →
      `buildClient(config)`，与参考实现 `_build_llm_client` 一致。
    - 无配置时 `resolveLlmConfig` 自己抛 `LlmError("No language model is configured...", "bad_request")`，
      因此 `test_preferences.py#test_ws_reports_structured_error_when_no_model` 仍通过。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| `chat.db` DDL 与参考实现不一致 | 旧库打不开 | 用例断言 `PRAGMA table_info` 的列名与顺序 |
| `getSession` 返回 `null` 而非抛错 | 未知会话被当作「新会话」静默建档 | 用例断言抛 `UnknownSession` |
| `updateSession` 把 `preferences` 整体替换 | 丢 `llm_selection` | 用例断言部分更新后 `llm_selection` 仍在 |
| `llm_selection` 未回写 `model_id` | 前端下拉显示错 | 用例断言两处一致 |
| 空消息落库 | 会话里出现空 user 行 | 用例断言 `messages` 为空 |
| `turn_end` 用旧快照的 title | 前端标题栏不更新 | 用例断言 `turn_end.title` 等于首条消息前 40 字 |
| SSE 攒批 | 前端收不到增量 | 用例断言响应体含多个 `data:` 帧 |
| 设置落盘破坏 `SettingsApiTest` | 既有 690 项回退 | 归一规则原样搬移；断言不改 |
| 导航工具在 chat 里被当成 mastery 工具门控 | 只读工具被拒 | `ToolClassification` 把 4 个导航工具列为共享；chat 的 `sessionMode` 为 `null`（不门控） |

## Migration Plan

1. `SettingsStore`（JSON 读写 + 归一）+ `ChatStore`（`chat.db`）。
2. `ModelResolution`（`normalizeSelection` / `resolveLlmConfig` / `buildClient`）。
3. `ChatService` 基础（CRUD + `streamTurn` 无工具分支）。
4. `ChatService` 导航工具回合。
5. `ChatController`（CRUD + SSE）。
6. `TurnRunner` chat 分支 + `WebConfig` 接 `ModelResolution` + `SettingsController` 委托 store。
7. 测试：`ChatStoreTest` / `SettingsStoreTest` / `ModelResolutionTest` / `ChatServiceTest` / `ChatApiTest`。
8. `verify.ps1 -Scope api` → 全量 → `pytest tests/test_chat_nav.py tests/test_preferences.py tests/test_knowledge_chat.py -q` 对照 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/chat/` + `agents/model_resolution.py` 为唯一依据。
- `test_knowledge_chat.py` 的 chat 部分（`test_chat_stream_persists_messages`、
  `test_chat_injects_knowledge_context`、`test_chat_without_model_reports_error`）本 change 可对照；
  其中「注入知识库参考块」需要 `knowledge_context`，属 `0021`，故该条只对照前两条与第三条。
