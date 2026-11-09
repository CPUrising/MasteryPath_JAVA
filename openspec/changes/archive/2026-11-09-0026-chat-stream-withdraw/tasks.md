# Tasks

## 1. 流式增量

- [x] 1.1 `LlmClient` 增加惰性 `streamChat(...)`，默认委托 `streamChatEvents(...)`；验证：`OpenAiCompatibleClientTest` 与 `AgentLoopTest` 编译通过。
- [x] 1.2 `OpenAiCompatibleClient` 覆盖 `streamChat`，逐行解析 SSE 并在流结束/异常时关闭底层流；验证：`streamChatIsLazy` 用例在 2 秒内取到第一片。
- [x] 1.3 `AgentLoop` 与 `ChatService.plainStream` 改为惰性消费；验证：`ChatApiTest` 的 SSE 用例仍能收到 `text_delta` 与 `turn_end`。

## 2. 消息撤回

- [x] 2.1 `ChatStore.deleteMessagesFrom` 按 `id >= messageId` 截断并刷新 `updated_at`；验证：`ChatStoreTest#deleteMessagesFromTruncatesTail`。
- [x] 2.2 `ChatController` 新增 `DELETE /chat/sessions/{sessionId}/messages/{messageId}`；验证：`ChatApiTest#withdrawTruncatesTail` 覆盖 204、截断与 404。

## 3. 前端 UX

- [x] 3.1 移除聊天页 `dark` 主题，气泡与输入区工具栏统一为掌握之路浅色样式；验证：`/chat` 计算样式背景为 `#ffffff` 且无 `dark` 类。
- [x] 3.2 助手消息 Markdown 渲染、消息复制、用户消息撤回并回填输入框，补齐 i18n 文案；验证：`npm run typecheck` 与 `next build` 通过，页面可交互。

## 4. 集成验证

- [x] 4.1 后端全量 `mvnw test` 通过，前端 `typecheck`/`build` 通过；验证：`.\scripts\verify.ps1 -Change 0026-chat-stream-withdraw` 产出日志与证据。

## Workflow follow-up

- 运行 `.\scripts\verify.ps1 -Change 0026-chat-stream-withdraw`，产出 `docs/verify/0026-chat-stream-withdraw.md`。
- 追加 `docs/regression-log.md`，并新增 `docs/ai-collaboration/0026-chat-stream-withdraw.md` 留痕。
- 人类审查证据后执行 `openspec archive 0026-chat-stream-withdraw`。
