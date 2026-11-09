# Proposal: 聊天真流式输出与对话撤回

## Why

普通聊天虽然使用 SSE，但 LLM 适配层把上游增量全部读完才返回 `List<StreamChunk>`，
回合链路（`AgentLoop` / `ChatService`）拿到的是攒批结果，用户实际看到「长时间等待 → 一次性出现」，
与 `agent-turn` 已冻结的「流式文本增量」契约不符。同时，对话缺少消息级撤回能力：
用户发错消息后只能删除整个会话，无法回到发送前的上下文。

## What Changes

- **真流式（行为等价修复）**
  - `LlmClient` 新增惰性 `streamChat(...)`；`OpenAiCompatibleClient` 逐行解析上游 SSE，文本片即时交付。
  - `AgentLoop` 与 `ChatService` 改为惰性消费，不再预收整段；普通聊天 SSE 与导师 WS 回合同时生效。
  - 保留 `streamChatEvents(...)` 的一次性收集接口，供测试与只需完整结果的调用方使用。
- **消息撤回（新增行为）**
  - 新增 `DELETE /chat/sessions/{sessionId}/messages/{messageId}`：删除该消息及其之后的所有消息，成功返回 `204`，未知会话返回 `404`。
  - `ChatStore` 新增按 `id >= messageId` 截断删除的方法，并刷新会话 `updated_at`。
- **前端 UX（人类批准的范围例外，见 Impact）**
  - 聊天页移除强制深色主题，与「掌握之路」统一为浅色视觉。
  - 输入区工具栏（模型、知识库、附件、能力标签）统一为浅色样式。
  - 助手消息支持 Markdown 渲染；消息支持复制；用户消息支持撤回并回填输入框。

## Capabilities

### New Capabilities

- `chat-session`: 普通聊天会话与回合的对外契约——SSE 增量输出、回合持久化，以及消息级撤回语义。

### Modified Capabilities

无。流式增量已由 `agent-turn` 的 Turn Orchestration 要求覆盖，本次是让实现回到该契约，不改变规范文本。

## Impact

- 影响代码（Java 仓库）：
  - `src/main/java/com/masterypath/adapter/llm/LlmClient.java`
  - `src/main/java/com/masterypath/adapter/llm/OpenAiCompatibleClient.java`
  - `src/main/java/com/masterypath/adapter/loop/AgentLoop.java`
  - `src/main/java/com/masterypath/service/ChatService.java`
  - `src/main/java/com/masterypath/storage/ChatStore.java`
  - `src/main/java/com/masterypath/adapter/web/ChatController.java`
  - 对应 JUnit：`OpenAiCompatibleClientTest`、`ChatApiTest`、`ChatStoreTest`
- 影响代码（前端仓库 `../MasteryPath/web`，**范围例外**）：
  - `components/chat/ChatWorkspace.tsx`、`ChatMessageList.tsx`、`MarkdownMessage.tsx`（新增）
  - `components/common/MessageComposer.tsx`
  - `lib/chat-api.ts`、`i18n/{zh,en}.json`、`app/globals.css`、`package.json`
- 范围例外与授权：
  - `openspec/project.md` 的 Out of scope 写明「不改写前端」「不新增产品功能」。
  - 人类于 2026-11-09 明确要求整改聊天页视觉与对话操作，并批准「保留前端改动 + 新增撤回端点」作为本 change 的例外；本文档即例外记录。
  - 例外仅限聊天页 UX 与消息撤回，不扩大到其他前端模块或其他产品功能。
- 兼容性：
  - 新增端点是对参考实现的协议增补，不影响既有 REST/WS 路径与 JSON 字段。
  - 既有 `streamChatEvents(...)` 语义不变，现有测试与调用方无需改动。
- 明确不做：
  - 不新增停止生成、重新生成、消息编辑等其它对话操作。
  - 不修改 `openspec/specs/` 基线；基线更新由本 change 归档时完成。
