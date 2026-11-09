# Tasks

## 1. 迁移

- [x] 1.1 复制 `../MasteryPath/web` 到本仓库 `web/`，排除 `node_modules`、`.next`、`out`、`dev*.log`、`*.tsbuildinfo`、`next-env.d.ts` 与误建文件 `cd`、`npm`；验证：`web/package.json`、`web/app`、`web/components` 存在且 `git status` 未出现构建产物。
- [x] 1.2 `web/.gitignore` 补齐 `.env*.local`；验证：`git check-ignore -v web/.env.local` 命中。
- [x] 1.3 标记前端归属：确认 `package.json` / `package-lock.json` 入库；验证：`git status --short` 中二者为待新增。

## 2. 渲染统一

- [x] 2.1 新增 `web/components/common/MarkdownMessage.tsx`（唯一 Markdown 实现）；验证：`npm run typecheck`。
- [x] 2.2 新增 `web/components/common/MessageBubble.tsx`（气泡 + 复制 + 可选撤回 + 流式光标）；验证：`npm run typecheck`。
- [x] 2.3 普通聊天 `ChatMessageList` 与掌握之路会话页改用 `MessageBubble`；验证：`grep MarkdownMessage` 仅命中 `common/`。
- [x] 2.4 删除重复的 `web/components/chat/MarkdownMessage.tsx`；验证：无引用残留，`npm run build` 通过。

## 3. 文档同步

- [x] 3.1 `openspec/project.md`：In scope/Out of scope/技术栈中的 `../MasteryPath/web` 与「前端不复制进本仓库」改为 `web/`。
- [x] 3.2 `README.md` §3 启动前端的路径改为仓库内 `web/`。
- [x] 3.3 `docs/migration-plan.md` 中前端路径与策略表述同步。

## 4. 验证

- [x] 4.1 `web/` 下 `npm ci` + `npm run typecheck` + `npm run build` 全部通过。
- [x] 4.2 `.\scripts\verify.ps1 -Change 0027-web-into-repo` 通过，产出 `docs/verify/0027-web-into-repo.md`。

## Workflow follow-up

- 追加 `docs/regression-log.md`。
- 新增 `docs/ai-collaboration/0027-web-into-repo.md` 留痕。
- 人类审查证据后执行 `openspec archive 0027-web-into-repo`。
