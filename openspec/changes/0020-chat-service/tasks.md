# Tasks

## 1. 存储

- [x] 1.1 `storage/SettingsStore`：`settings.json` 读写 + 分组归一（模型分组折叠 / 提供方协议白名单）
- [x] 1.2 `storage/ChatStore`：独立 `chat.db`（`sessions` + `messages`），DDL 逐字一致 + `_migrate`

## 2. 模型解析

- [x] 2.1 `adapter/llm/ModelResolution`：`normalizeSelection` / `resolveLlmConfig` / `buildClient`

## 3. 聊天服务

- [x] 3.1 `service/ChatService` 会话 CRUD（`listSessions` / `createSession` / `sessionDetail` / `updateSession` / `deleteSession`）
- [x] 3.2 `service/ChatService.streamTurn`：空消息/未知会话短路、历史装配、落库、`turn_end`
- [x] 3.3 `service/ChatService` 导航工具回合：4 个只读工具 + 系统提示追加

## 4. 传输与接线

- [x] 4.1 `adapter/web/ChatController`：`/chat/sessions` CRUD + `/turns` 的 SSE
- [x] 4.2 `TurnRunner` chat 分支接 `ChatService`；`WebConfig` 用 `ModelResolution` 构造 `ClientBuilder`；`SettingsController` 委托 `SettingsStore`

## 5. 测试与验证

- [x] 5.1 `ChatStoreTest` + `SettingsStoreTest` + `ModelResolutionTest`
- [x] 5.2 `ChatServiceTest`（含 `test_chat_nav.py` 对照）+ `ChatApiTest`（SSE + CRUD）
- [x] 5.3 `.\scripts\verify.ps1 -Scope api -Change 0020-chat-service` → 全量 → `pytest tests/test_chat_nav.py tests/test_preferences.py -q` 对照
- [x] 5.4 回填 `docs/verify/0020-chat-service.md`、`docs/regression-log.md`、`docs/traceability.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0020-chat-service.md`、`README.md`
