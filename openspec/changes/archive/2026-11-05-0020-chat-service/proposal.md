# Proposal: 普通聊天服务

## Why

`0019` 把掌握式学习的 WS 通道打通了，但 `TurnRunner.run()` 的 `chat` 分支至今抛
`UnsupportedOperationException`——前端点「普通聊天」会 500。

参考实现 `chat/`（`store.py` 232 + `service.py` 248 行）+ `agents/model_resolution.py`（113 行）
+ `api/chat_routes.py`（83 行）是这条通道的全部逻辑。它与 mastery 的关键差异：

| 维度 | mastery | 普通聊天 |
| --- | --- | --- |
| 持久化 | `learning.db` 的 `sessions` / `messages` | **独立 `chat.db`**（表结构不同） |
| 模式 | 三模式门控 | **无模式**（`session_mode=None`） |
| 工具 | 23 个（19 mastery + 4 导航） | **只有 4 个导航工具**（只读） |
| 模型 | `ClientBuilder`（`0022` 前抛未配置） | `resolve_llm_config`（本 change 落地） |
| 传输 | `/ws` | `/chat/sessions/{id}/turns` 的 **SSE** |

没有这一层，`test_chat_nav.py` 无法通过，前端「聊天」入口不可用。

## What Changes

- 新增 `storage/ChatStore`：独立 `chat.db`（`sessions` + `messages`），DDL 与参考实现逐字一致
- 新增 `service/ChatService`：会话 CRUD、`streamTurn`（含导航工具回合）、标题自动填充
- 新增 `adapter/llm/ModelResolution`：`resolveLlmConfig` / `buildClient`（设置 → 客户端）
- 新增 `adapter/web/ChatController`：`/chat/sessions` CRUD + SSE 回合端点
- `TurnRunner` 的 `chat` 分支接上 `ChatService`
- `SettingsController` 从内存改为委托 `SettingsStore`（落盘 `settings.json`）

## Capabilities

### New Capabilities

无新增能力；实现 `specs/agent-turn` 的 chat 契约（行为以参考实现 `chat/` 为唯一依据）。

### Modified Capabilities

无。`skip_specs: true`：本 change 补齐参考实现已有、而 `0001`–`0019` 尚未实现的能力。

## Impact

- 影响文件：
  - `src/main/java/com/masterypath/storage/ChatStore.java`（新增）
  - `src/main/java/com/masterypath/service/ChatService.java`（新增）
  - `src/main/java/com/masterypath/storage/SettingsStore.java`（新增）
  - `src/main/java/com/masterypath/adapter/llm/ModelResolution.java`（新增）
  - `src/main/java/com/masterypath/adapter/web/ChatController.java`（新增）
  - `src/main/java/com/masterypath/adapter/turn/TurnRunner.java`（chat 分支）
  - `src/main/java/com/masterypath/adapter/web/SettingsController.java`（改为委托 store）
  - `src/test/java/com/masterypath/**/Chat*Test.java`（新增）
  - `scripts/verify.ps1`（`-Scope api` 补 `*Chat*Test`）
- 影响测试：既有 690 个用例**不修改**（`SettingsApiTest` 需适配落盘，断言不变）。
- 明确不做（Out of scope）：
  - 知识库检索与 `knowledge_context` 真实实现 → 属 `0021-knowledge-base`
  - 提供方探测（`/settings/providers/probe`）与 Task Model → 属 `0022-settings-task-model`
  - 大纲评测 CLI → 属 `0023-outline-eval-cli`
  - 验收 → 属 `0024-acceptance`
