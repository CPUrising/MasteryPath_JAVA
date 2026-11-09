# AI 协作留痕：0026-chat-stream-withdraw

## 1. prompt 范围

- 授权修改的文件（Java 仓库）：
  - `src/main/java/com/masterypath/adapter/llm/LlmClient.java`
  - `src/main/java/com/masterypath/adapter/llm/OpenAiCompatibleClient.java`
  - `src/main/java/com/masterypath/adapter/loop/AgentLoop.java`
  - `src/main/java/com/masterypath/service/ChatService.java`
  - `src/main/java/com/masterypath/storage/ChatStore.java`
  - `src/main/java/com/masterypath/adapter/web/ChatController.java`
  - `src/test/java/com/masterypath/adapter/llm/OpenAiCompatibleClientTest.java`
  - `src/test/java/com/masterypath/adapter/web/ChatApiTest.java`
  - `src/test/java/com/masterypath/storage/ChatStoreTest.java`
- 授权修改的文件（前端仓库 `../MasteryPath/web`，人类批准的例外）：
  - `components/chat/ChatWorkspace.tsx`、`ChatMessageList.tsx`、`MarkdownMessage.tsx`（新增）
  - `components/common/MessageComposer.tsx`
  - `lib/chat-api.ts`、`i18n/{zh,en}.json`、`app/globals.css`、`package.json` / `package-lock.json`
- 明确不做的事：
  - 不实现停止生成、重新生成、消息编辑、软删除与撤回审计事件。
  - 不改 `openspec/specs/` 基线；不改学习侧 `learning.db` 的消息契约。
  - 不把前端构建纳入 `verify.ps1`（前端仓库不在本仓库验证入口范围内）。
- 出处：用户指令「主页聊天背景与掌握之路不同、对话框设置、没有流式输出、Markdown/复制/撤回」；
  随后用户以「本仓库使用 OpenSpec + SDD」指出流程缺失，并批准「补建 0026 + 保留前端改动与撤回端点为例外」。

## 2. AI 产出

- 流式：
  - `LlmClient` 新增惰性 `streamChat(...)`，默认回退到 `streamChatEvents(...)`。
  - `OpenAiCompatibleClient` 覆盖 `streamChat`，用 `BodyHandlers.ofLines()` + `SseChunkIterator` 逐行解析，
    流耗尽/异常时关闭底层流；`streamChatEvents(...)` 改为收集 `streamChat` 的结果。
  - `AgentLoop` 用 `Iterator<StreamChunk>` 替代 `List + index`；`ChatService.plainStream` 惰性映射。
- 撤回：
  - `ChatStore.deleteMessagesFrom(sessionId, messageId)` 按 `id >= ?` 截断并刷新 `updated_at`。
  - `ChatController` 新增 `DELETE /chat/sessions/{sessionId}/messages/{messageId}`。
- 前端：
  - 移除 `dark`，聊天页与掌握之路统一浅色；气泡改为 `bg-primary/10` / `bg-muted`。
  - 输入区模型选择器改为浅色描边胶囊；新增 `draft` 注入用于撤回回填。
  - 新增 `MarkdownMessage`（react-markdown + remark-gfm）、消息复制、用户消息撤回。
  - 回合结束后回拉会话详情，取得服务端消息 id，撤回有据可依。

## 3. 人工修正点

AI 自我修正 3 轮：

1. **流程偏差（用户指出）**：首轮直接改代码，未先 `openspec new change`。
   修正方式：补建 `0026-chat-stream-withdraw`，补齐 proposal / specs / design / tasks，
   `openspec validate --strict` 通过后重跑 `verify.ps1` 产出证据。未删除已写代码，也未伪造证据。
2. **`LlmClient` 默认实现递归风险**：初版把 `streamChatEvents` 改为委托 `streamChat`，
   而 `streamChat` 又委托 `streamChatEvents`，只实现 `chat` 的客户端会无限递归。
   修正：`streamChatEvents` 保留回退 `chat`，`streamChat` 默认收集前者；真实客户端覆盖 `streamChat`。
3. **撤回测试覆盖不足**：初版只测「从第一条撤回后为空」，未覆盖「保留更早消息」与「幂等」。
   修正：新增 `ChatStoreTest#deleteMessagesFromKeepsEarlier`，并在 `ChatApiTest#withdrawTruncatesTail`
   增加重复撤回断言，随后重跑全量验证（864 → 868）。

未出现「放宽断言」或「删除用例」的情况。

## 4. 回归结果

- 用例数 / 失败数：全量 **868 / 0**（上一基线 864，新增 4 个用例）
- 证据链接：[`docs/verify/0026-chat-stream-withdraw.md`](../verify/0026-chat-stream-withdraw.md)
- 日志：`artifacts/verify-0026-chat-stream-withdraw-20261109-154233.log`
- 前端：`npm run typecheck`、`npm run build` 均通过（手工命令，不计入 Java 用例数）

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| 是否先回滚再走流程 | **不回滚** | 用户选择「补建 0026，保留代码」；代码作为 Build 产物，补齐 Plan/Check/Verify 证据链 |
| 前端改动是否越界 | **是，但已获人类批准** | `project.md` Out of scope 明确不改前端；用户在 2026-11-09 要求并批准为例外，记录在 proposal Impact |
| 新增撤回端点是否算新功能 | **是** | 参考实现无该端点；写入 `chat-session` delta spec，并在 proposal 声明协议增补 |
| 撤回是否软删除 | **否** | 软删除会改 `messages` DDL，违反「表结构逐字一致」；截断删除更符合撤回语义 |
| `messageId` 不存在时返回什么 | **204** | 幂等便于前端重试；spec 场景已固化 |
| 惰性迭代器是否可能悬挂连接 | **已缓解** | 流耗尽或抛错即关闭底层 `Stream`；回合由 Web 容器线程驱动 |
| Markdown 是否允许原始 HTML | **不允许** | react-markdown 默认转义；避免模型输出注入 |
| 前端构建是否算「已验证」 | **算手工证据，不算 CI** | §2.3 记录命令与浏览器观测，明确不冒充自动化证据 |
