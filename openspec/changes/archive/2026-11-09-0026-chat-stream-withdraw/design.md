# Design: 聊天真流式输出与对话撤回

## Context

- 现有 `LlmClient.streamChatEvents(...)` 返回 `List<StreamChunk>`。`OpenAiCompatibleClient` 用
  `lines.forEach(...)` 把上游 SSE 读完再返回；`AgentLoop` 与 `ChatService.plainStream` 也都先收成列表。
  SSE 传输层（`ChatController`）本身逐帧 `flush`，因此瓶颈只在适配层与回合链路。
- 参考实现（`../MasteryPath/masterypath/agents/llm.py`、`chat/service.py`）使用生成器逐片 `yield`，
  Java 侧属于实现偏差，不是规范变化。
- `ChatStore` 只有会话级删除，没有消息级操作；前端消息在发送后是本地乐观对象，没有服务端 id。
- 前端 `ChatWorkspace` 根节点强制 `dark`，与 `LearningShell` 的浅色主题不一致。

## Goals / Non-Goals

**Goals:**

- 普通聊天与导师回合都能在上游产出文本片后立即向前端交付，不攒批。
- 提供可幂等调用、按消息截断的撤回端点，并让前端撤回后能继续编辑重发。
- 聊天页视觉与「掌握之路」统一，补齐 Markdown、复制、撤回等基础操作。

**Non-Goals:**

- 不引入响应式流（Reactor/Flow）、不改变 SSE/WS 帧格式与字段名。
- 不实现停止生成、重新生成、消息编辑、软删除或撤回审计事件。
- 不改动学习侧（`learning.db`）的消息与回合契约。

## Decisions

### 1. 在 `LlmClient` 增加惰性 `streamChat(...)`，保留 `streamChatEvents(...)`

- 新方法返回 `Iterator<StreamChunk>`；默认实现委托 `streamChatEvents(...)`，保证只实现 `chat` 的客户端仍可用。
- `OpenAiCompatibleClient` 覆盖 `streamChat`：用 `HttpResponse.BodyHandlers.ofLines()` 拿到行流，
  包装成内部 `SseChunkIterator`，按需喂给 `SseParser`；文本片即时出队，工具调用在 `finish()` 时一次产出。
  流耗尽或抛错时关闭底层 `Stream`，避免连接泄漏。
- `streamChatEvents(...)` 改为「收集 `streamChat` 的结果」，这样现有测试与调用方语义不变。
- 备选：把接口整体改成回调或 `Flow.Publisher`。放弃原因：侵入面大，且会改变假客户端与测试写法。

### 2. `AgentLoop` 与 `ChatService` 改为惰性消费

- `AgentLoop.EventIterator` 用 `Iterator<StreamChunk>` 取代 `List + index`，每轮 `START_ROUND` 才创建流。
- `ChatService.plainStream` 用匿名迭代器把文本片映射成 `StreamEvent.textDelta`，不再预收整段。
- 保持「先文本、后工具」的事件顺序：文本片必须在工具执行前到达调用方。

### 3. 撤回按 `id >= messageId` 截断，幂等返回 204

- `ChatStore.deleteMessagesFrom(sessionId, messageId)` 在 `session_id` 限定下执行
  `DELETE ... WHERE id >= ?`，并刷新 `updated_at`；会话不存在时抛 `UnknownSession` → `404`。
- `messageId` 不存在时删除 0 行仍返回 `204`，便于前端重试而不需要额外分支。
- 备选：软删除（`deleted_at` 列）。放弃原因：会改变 `messages` DDL，违反「表结构逐字一致」约束，
  且撤回语义要求后续消息一起失效，截断更直接。

### 4. 前端发送后回拉会话详情，撤回基于服务端 id

- `ChatWorkspace` 在每回合结束后（`finally`）调用 `chatApi.get(sessionId)`，用服务端消息替换本地乐观消息，
  从而拿到 `serverId`；撤回时调用新端点，成功后本地截断并把原文注入输入框。
- `MessageComposer` 新增可选 `draft` 属性，通过 `nonce` 触发一次文本注入与聚焦。
- 撤回按钮在 `streaming` 期间禁用，避免与进行中的回合竞争。

### 5. 前端 Markdown 与主题

- 使用 `react-markdown` + `remark-gfm` 渲染助手消息；默认不允许原始 HTML，链接强制 `target="_blank"` + `rel="noreferrer"`。
- Markdown 样式集中在 `globals.css` 的 `.chat-markdown`，全部使用主题变量，浅色/深色均可读。
- 移除聊天页根节点的 `dark`，气泡改用掌握之路的 `bg-primary/10`（用户）与 `bg-muted`（助手）。

## Risks / Trade-offs

- [反向代理缓冲 SSE] → `ChatController` 已逐帧 `flush` 并设置 `X-Accel-Buffering: no`；前端直连后端端口时不经过代理。
- [迭代器未消费完导致连接悬挂] → 流耗尽或抛错时关闭底层 `Stream`；回合由 Web 容器线程驱动，异常会终止响应。
- [上游分片粒度很细导致前端频繁重渲染] → 聊天回答通常为短文本，React 状态更新可接受；未引入额外节流以免破坏「即时」语义。
- [乐观消息尚未 sync 时撤回] → 撤回仅在非 streaming 时可用，且回合结束后立即 sync；未同步时退化为本地截断。
- [新增端点偏离参考实现] → 已知并在 proposal 中记录为人类批准的范围例外；不影响既有契约。
- [react-markdown 增加包体积] → 只在聊天页组件引入，构建产物按路由拆分。

## Migration Plan

- 无数据迁移；旧 `chat.db` 直接兼容，新方法只执行 `DELETE`。
- 部署：重启 Java 后端；前端执行 `npm install`（新增 `react-markdown`、`remark-gfm`）后重启 dev server。
- 回滚：撤回端点与惰性流均为增量改动；回滚代码即可，数据库无需回滚（被撤回的消息无法恢复，属于用户显式操作）。

## Open Questions

无。
