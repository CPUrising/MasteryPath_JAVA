# AI 协作留痕：0027-web-into-repo

## 1. prompt 范围

- 授权动作：把 `../MasteryPath/web` 源码复制进本仓库 `web/`，并完成聊天与掌握之路的渲染统一。
- 授权修改的文件：
  - `web/`（新增目录，79 个源码文件）
  - `web/.gitignore`
  - `web/components/chat/ChatMessageList.tsx`
  - `web/app/(workspace)/learning/mastery/[pathId]/sessions/page.tsx`
  - 新增 `web/components/common/MarkdownMessage.tsx`、`web/components/common/MessageBubble.tsx`
  - 删除 `web/components/chat/MarkdownMessage.tsx`
  - `openspec/project.md`、`README.md`、`docs/migration-plan.md`
- 明确不做的事：
  - 不修改、不删除 `../MasteryPath`（含其 `web/`）任何文件。
  - 不改 Java 源码、测试、数据库结构与 API。
  - 不新增前端测试框架，不改交互契约。
- 出处：用户指令「只处理 `D:\documents\AGENT-learning\MasteryPath_JAVA` 内的这个范围」，
  以及结构化选择「把 web 并入 MasteryPath_JAVA」「复制并保留原仓库」「一起并入 A 方案改动」。

## 2. AI 产出

- 迁移：
  - 用 `robocopy /E` 复制，排除 `node_modules`、`.next`、`out`、`dev*.log`、`*.tsbuildinfo`、`next-env.d.ts`
    与误建的 0 字节文件 `cd`、`npm`。
  - `web/.gitignore` 增加 `.env*.local`，`web/.env.local` 留在本地不入库。
- 渲染统一：
  - `components/common/MarkdownMessage.tsx` 成为唯一 Markdown 实现（react-markdown + remark-gfm，
    外链 `target="_blank" rel="noreferrer"`）。
  - `components/common/MessageBubble.tsx` 承载角色样式、复制、可选撤回与流式光标。
  - 普通聊天 `ChatMessageList` 与掌握之路会话页均改用 `MessageBubble`；删除重复的
    `components/chat/MarkdownMessage.tsx`。
- 文档：
  - `openspec/project.md`、`README.md`、`docs/migration-plan.md` 的前端路径与范围表述改为仓库内 `web/`。

## 3. 人工修正点

1. **范围理解偏差（用户指出）**：AI 曾把「只处理 Java 仓库」理解为「不动另一个 git 仓库」，
   用户澄清是指 `D:\documents\AGENT-learning\MasteryPath_JAVA` 这个目录范围。
   修正：给出三个选项，人类选择「把 web 并入 MasteryPath_JAVA」，本 change 即该决策的落地产物。
2. **重复组件**：A 方案草稿新增了 `common/MarkdownMessage.tsx`，与已提交的 `chat/MarkdownMessage.tsx` 重复。
   修正：迁移后删除 `chat/` 版本，`grep` 确认无残留引用。
3. **构建产物泄漏风险**：复制时若带上 `node_modules`/`.next` 会污染仓库。
   修正：robocopy 显式排除，并在 `npm run build` 后再查 `git status`，确认无构建产物进入待提交列表。

未出现「放宽断言」「删除用例」「伪造验证结果」的情况；Java 侧零改动。

## 4. 回归结果

- 用例数 / 失败数：全量 **868 / 0**（与上一基线相同，未新增 Java 用例）
- 证据链接：[`docs/verify/0027-web-into-repo.md`](../verify/0027-web-into-repo.md)
- 日志：`artifacts/verify-0027-web-into-repo-20261109-163954.log`
- 前端：`npm ci`、`npm run typecheck`、`npm run build` 均通过（手工命令，不计入 Java 用例数）

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| 复制还是移动 | **复制** | 人类选择「复制并保留原仓库」；避免破坏参考实现的可用前端 |
| 是否重写前端 | **否** | 只改源码位置与组件组织，REST/WS/JSON 契约不变；`skip_specs: true` |
| `project.md` Out of scope 冲突 | **已修订并留痕** | 原文「不改写前端」改为「不重写前端」；proposal Impact 与 verify D1 记录 |
| 掌握之路是否传 `onWithdraw` | **否** | 导师会话消息无独立 REST 撤回端点；只在普通聊天启用，避免出现点了没反应的按钮 |
| Markdown 是否允许原始 HTML | **不允许** | react-markdown 默认转义，避免模型输出注入 |
| 是否合并两套对话链路 | **否** | 人类选择「A. 只统一渲染」；`ChatService`（SSE）与 `TurnRunner`（WS）保持各自职责 |
| 前端构建是否算「已验证」 | **算手工证据，不算 CI** | verify 报告 §2.2 如实记录命令与输出 |
