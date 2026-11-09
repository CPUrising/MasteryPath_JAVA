# Design: 前端并入本仓库并统一消息渲染

## 1. 目标结构

```
MasteryPath_JAVA/
├── src/                     # Java 后端（不变）
├── openspec/                # 规范与 change（不变）
├── docs/                    # 验证与协作记录（不变）
└── web/                     # Next.js 14 前端（本次迁入）
    ├── app/
    ├── components/
    │   ├── chat/
    │   ├── common/          # MessageBubble / MarkdownMessage / MessageComposer
    │   ├── learning/ ...
    ├── lib/
    ├── i18n/
    └── package.json
```

后端与前端在同一个 git 仓库、同一个 `.gitignore` 根下，但构建互不干扰：
Maven 只处理 `src/` 与 `pom.xml`，npm 只在 `web/` 内执行。

## 2. 迁移方式

- 采用「复制并保留原仓库」：`../MasteryPath/web` 保持原样，不做删除。
- 复制排除项：`node_modules/`、`.next/`、`out/`、`dev*.log`、`*.tsbuildinfo`、`next-env.d.ts`。
- 误建的 0 字节文件 `web/cd`、`web/npm` 不复制。
- `.env.local`（仅 `NEXT_PUBLIC_API_BASE=http://127.0.0.1:8080`）复制但不入库，由 `web/.gitignore` 的 `.env*.local` 忽略。

## 3. 渲染统一策略（A 方案：只统一渲染）

已确认两套对话链路（普通聊天 `ChatService` + SSE；掌握式导师 `TurnRunner` + WS）不合并，
本次只抽取**渲染层**：

| 层 | 组件 | 职责 |
| --- | --- | --- |
| 渲染原子 | `components/common/MarkdownMessage.tsx` | 唯一的 Markdown → DOM 映射，GFM 表格/列表/代码块 |
| 渲染单元 | `components/common/MessageBubble.tsx` | 气泡布局、角色样式、复制、可选撤回按钮、流式光标 |
| 调用方 | `ChatMessageList` / 掌握之路会话页 | 只负责把数据映射成 `MessageBubble` 的 props |

接口约定：

- `role: "user" | "assistant"`；用户文本 `pre-wrap`，助手文本走 Markdown。
- `attachments?: { name: string }[]` 只显示文件名，不参与点击。
- `streaming?: boolean`：内容为空时显示脉冲光标。
- `onWithdraw?: () => void`：只有调用方能持久化删除时才传入；掌握之路本次不传。

## 4. 依赖与样式

- 依赖沿用已提交的 `react-markdown@^9.1.0`、`remark-gfm@^4.0.1`，不新增包。
- Markdown 样式使用既有 `app/globals.css` 的 `.chat-markdown` 作用域，避免污染 Tailwind 基础样式。
- 外链统一 `target="_blank" rel="noreferrer"`。

## 5. 验证策略

- 后端：`.\scripts\verify.ps1 -Change 0027-web-into-repo`，确认 Java 侧零回归。
- 前端：在 `web/` 下 `npm ci` → `npm run typecheck` → `npm run build`。
- 人工：`/chat` 与 `/learning/mastery/<id>/sessions` 两处气泡样式一致，助手 Markdown 正常渲染，复制可用。
