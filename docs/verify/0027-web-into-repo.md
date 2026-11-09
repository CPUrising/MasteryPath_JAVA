# 验证报告：0027-web-into-repo

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0027-web-into-repo` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-09 16:40:38（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.x（wrapper）、Spring Boot 3.5.16、Node 24.18.0、npm 11.16.0、Next.js 14.2.35 |
| 运行环境 | 本地（非 CI） |
| 后端命令 | `.\scripts\verify.ps1 -Change 0027-web-into-repo` |
| 后端日志 | `artifacts/verify-0027-web-into-repo-20261109-163954.log`（全量） |
| 结果包 | `target/surefire-reports/` |
| 前端命令 | `web/` 下 `npm ci` → `npm run typecheck` → `npm run build` |

## 2. 需求覆盖

本 change 为 `skip_specs: true`（仓库拓扑与前端渲染重构，无行为增量），因此按 tasks 逐项核对。

### 2.1 迁移

| Task | 证据 | 结果 |
|---|---|---|
| 1.1 复制源码到 `web/` | `git status --porcelain -uall -- web` 计 79 个文件；`web/package.json`、`web/app`、`web/components` 存在 | PASS |
| 1.2 忽略本地环境变量 | `git check-ignore -v web/.env.local` → `web/.gitignore:9:.env*.local` | PASS |
| 1.3 依赖清单入库 | `web/package.json`、`web/package-lock.json` 均出现在待新增列表 | PASS |

迁移排除项核对：`git status --porcelain -uall -- web` 中无 `node_modules`、`.next`、`*.tsbuildinfo`、`.env.local`、`dev*.log`；
误建文件 `cd`、`npm` 未复制。

### 2.2 渲染统一

| Task | 证据 | 结果 |
|---|---|---|
| 2.1 唯一 Markdown 实现 | `web/components/common/MarkdownMessage.tsx` | PASS |
| 2.2 唯一消息气泡 | `web/components/common/MessageBubble.tsx` | PASS |
| 2.3 两处调用方改用 `MessageBubble` | `ChatMessageList.tsx`、`learning/mastery/[pathId]/sessions/page.tsx` 均导入 `@/components/common/MessageBubble` | PASS |
| 2.4 删除重复组件 | `web/components/chat/MarkdownMessage.tsx` 已删除；`grep MarkdownMessage` 仅命中 `common/` 定义与 `MessageBubble` 引用 | PASS |

类型检查 `npm run typecheck` 无输出；生产构建 `npm run build` 生成 11 条路由（含 `/chat`、`/chat/[sessionId]`、
`/learning/mastery/[pathId]/sessions`），无错误。

### 2.3 文档同步

| Task | 证据 | 结果 |
|---|---|---|
| 3.1 `openspec/project.md` | §1、§2 In/Out of scope、§3 技术栈均改为仓库内 `web/` | PASS |
| 3.2 `README.md` §3 | 启动命令改为 `cd web` + `npm ci` | PASS |
| 3.3 `docs/migration-plan.md` | §1.1 增加前端源码位置；§1.2 增加 0027 注记 | PASS |

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 全量单元测试 | `.\scripts\verify.ps1 -Change 0027-web-into-repo` | BUILD SUCCESS，Tests run: **868**, Failures: 0, Errors: 0, Skipped: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（保持 `0026` 的 868 项） |
| 前端依赖安装 | `npm ci` | PASS（211 packages） |
| 前端类型检查 | `npm run typecheck` | PASS |
| 前端生产构建 | `npm run build` | PASS |

本 change 未新增或删除 Java 用例，总数维持 **868**。

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `openspec/project.md` 原 Out of scope 写「不改写前端」，本次把前端源码并入本仓库并新增共用组件 | 一般 | 人类于 2026-11-09 明确选择「把 web 并入 MasteryPath_JAVA」并选择「A. 只统一渲染」；不重写前端契约，已同步修订 project.md 表述并在 proposal Impact 记录 |
| D2 | 前端构建/类型检查不由 `verify.ps1` 产出 | 低 | 在 §2.2 如实记录命令与输出，不冒充自动化证据；`verify.ps1` 仍只覆盖 Java |
| D3 | 复制而非移动，`../MasteryPath/web` 仍存在 | 无 | 人类选择「复制并保留原仓库」；两仓库暂时并存，后续如需下线由人类另行决定 |
| D4 | `web/.env.local` 已复制但未入库 | 低 | 该文件只含 `NEXT_PUBLIC_API_BASE`，由 `web/.gitignore` 的 `.env*.local` 忽略 |

## 5. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 868, Failures: 0` 来自
`artifacts/verify-0027-web-into-repo-20261109-163954.log`。
前端 `npm ci` / `typecheck` / `build` 为本地手工执行，未计入 Java 用例总数。
归档前请人类复核 D1 的范围表述变更。
