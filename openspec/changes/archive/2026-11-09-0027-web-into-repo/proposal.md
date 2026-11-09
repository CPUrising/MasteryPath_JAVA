# Proposal: 前端并入本仓库并统一消息渲染

## Why

当前前端 `web/` 位于参考实现仓库 `../MasteryPath`，而后端已在本仓库重写。这带来三个具体问题：

1. **范围无法自洽**：人类要求「只处理 `MasteryPath_JAVA` 目录内的范围」，但聊天页视觉、Markdown、复制、撤回等 UX 改动只能落在 `../MasteryPath/web`，跨仓库改动既难评审也难回退。
2. **契约消费者与实现分居两地**：前端是后端 REST/WS 契约的消费者，二者不同步演进，改动协议时容易漏改。
3. **重复组件**：普通聊天与掌握之路各自内联消息气泡，Markdown/复制/撤回能力无法共享，样式容易再次漂移。

## What Changes

- **仓库拓扑**
  - 把 `../MasteryPath/web` 源码复制进 `MasteryPath_JAVA/web/`，本仓库自此单一管理后端 + 前端。
  - 排除构建产物与本地文件：`node_modules/`、`.next/`、`out/`、`dev*.log`、`*.tsbuildinfo`、`next-env.d.ts`；误建的 0 字节文件 `cd`、`npm` 不复制。
  - `web/.gitignore` 补齐 `.env*.local`，保留 `package.json` / `package-lock.json`。
  - 原仓库 `../MasteryPath` **不删除、不修改**（仅读取复制）。
- **渲染统一（A 方案）**
  - 新增 `web/components/common/MarkdownMessage.tsx`：唯一 Markdown 渲染实现。
  - 新增 `web/components/common/MessageBubble.tsx`：唯一消息气泡，含助手 Markdown、用户 pre-wrap、复制、可选撤回。
  - 普通聊天 `ChatMessageList` 与掌握之路会话页同时改为使用 `MessageBubble`。
  - 删除重复的 `web/components/chat/MarkdownMessage.tsx`。
- **文档同步**
  - `openspec/project.md`、`README.md`、`docs/migration-plan.md` 中 `../MasteryPath/web` 路径与「前端不复制进本仓库」的表述改为仓库内 `web/`。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。`skip_specs: true`：本 change 只改变源码所在仓库与前端组件组织方式，
REST/WS 路径、JSON 字段、WS 事件名、行为阈值均不变；`chat-session` 与 `agent-turn`
基线规范文本无需修改。

## Impact

- 新增目录：`web/`（Next.js 14 源码，约 90 个源码文件）。
- 新增依赖声明（沿用既有 `package.json`）：`react-markdown@^9`、`remark-gfm@^4`。
- 修改代码：
  - `web/components/chat/ChatMessageList.tsx`
  - `web/app/(workspace)/learning/mastery/[pathId]/sessions/page.tsx`
  - `web/.gitignore`
- 新增代码：
  - `web/components/common/MarkdownMessage.tsx`
  - `web/components/common/MessageBubble.tsx`
- 删除代码：`web/components/chat/MarkdownMessage.tsx`（被 `common/` 版本取代）。
- 修改文档：`openspec/project.md`、`README.md`、`docs/migration-plan.md`。
- 范围说明：
  - `openspec/project.md` 原 Out of scope 写「不改写前端」；本 change **不重写前端**，只改变其所在仓库，
    并同步修订该条目为「不改写前端行为契约」。
  - 人类于 2026-11-09 明确选择「把 web 并入 MasteryPath_JAVA」，并把已完成的统一渲染草稿一并并入。
- 兼容性：后端 Java 源码与测试零改动；`verify.ps1` 行为不变。
- 明确不做：
  - 不从 `../MasteryPath` 删除 `web/`。
  - 不新增产品功能、不改路由与 API。
  - 不引入前端测试框架。
