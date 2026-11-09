# 验证报告：0028-quiz-answer-parity

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0028-quiz-answer-parity` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-09 16:57:46（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.x（wrapper）、Spring Boot 3.5.16、Node 24.18.0、npm 11.16.0、Next.js 14.2.35 |
| 运行环境 | 本地（非 CI） |
| 后端命令 | `.\scripts\verify.ps1 -Change 0028-quiz-answer-parity` |
| 后端日志 | `artifacts/verify-0028-quiz-answer-parity-20261109-165700.log`（全量） |
| 结果包 | `target/surefire-reports/` |
| 前端命令 | `web/` 下 `npm run typecheck` → `npm run build` |
| OpenSpec | `openspec validate 0028-quiz-answer-parity --strict` 通过 |

## 2. 需求覆盖

本 change 为 `skip_specs: true`（前端展示修复，无后端行为增量），按 tasks 逐项核对。

### 2.1 渲染能力

| Task | 证据 | 结果 |
|---|---|---|
| 1.1 `MarkdownMessage` 增加 `className` / `inline` | `web/components/common/MarkdownMessage.tsx`：`inline` 用 `<span>` 容器并把 `p` 映射为 Fragment；默认参数保持 `<div class="chat-markdown text-[14px] leading-relaxed">` | PASS |
| 1.2 题干与选项改用 Markdown | `QuestionCard` 题干 `<MarkdownMessage className="text-sm font-medium" />`；选项 `<MarkdownMessage inline className="text-[13px]" />` | PASS |

### 2.2 作答反馈

| Task | 证据 | 结果 |
|---|---|---|
| 2.1 `grade` 返回类型补全 | `web/lib/api.ts` 新增 `GradeOutcome`（`status` / `result` / `is_correct` / `explanation` / `mastery_before` / `mastery_after` / `gate_cleared`） | PASS |
| 2.2 判分结果作为助手消息 | `sessions/page.tsx` 新增 `describeGrade` + `answerQuestion`：成功时把结论/解析/已掌握提示追加到 `items`，失败时显示 `判分失败 (…)`；随后 `load()` 刷新状态 | PASS |
| 2.3 中英文文案 | `i18n/zh.json`、`i18n/en.json` 新增 `q.verdict.*`、`q.mastered`、`q.gradeFailed` 共 6 个键，中英对齐 | PASS |

反馈消息复用 `MessageBubble`，因此自动具备 Markdown 渲染、复制按钮与统一气泡样式；
`load()` 只更新 `state`，不会覆盖 `items`。

### 2.3 输入区样式

| Task | 证据 | 结果 |
|---|---|---|
| 3.1 作答区对齐底部输入框 | `QuestionCard` 改为 `rounded-2xl border border-border bg-card p-2 shadow-sm focus-within:border-primary/40` + 无边框 `textarea` + 圆形 `ArrowUp` 按钮；Enter 提交、Shift+Enter 换行、空内容禁用 | PASS |

### 2.4 构建

`npm run typecheck` 无输出；`npm run build` 编译成功，11 条路由全部生成，
`/learning/mastery/[pathId]/sessions` 体积 5.39 kB。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 全量单元测试 | `.\scripts\verify.ps1 -Change 0028-quiz-answer-parity` | BUILD SUCCESS，Tests run: **868**, Failures: 0, Errors: 0, Skipped: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（保持 `0027` 的 868 项） |
| 前端类型检查 | `npm run typecheck` | PASS |
| 前端生产构建 | `npm run build` | PASS |

本 change 未改动 Java 源码与测试，总数维持 **868**。

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 判分反馈为本地追加，刷新页面后不保留 | 低 | 人类于 2026-11-09 选择「仅前端显示」；后端 `interactions` 仍记录判分结果，已在 proposal 声明限制 |
| D2 | 前端证据不由 `verify.ps1` 产出 | 低 | §2.4 如实记录 `typecheck` / `build` 输出，不冒充自动化证据 |
| D3 | `ungraded` 走 422 → `catch` 分支，显示后端英文原因 | 低 | 属于「未判定」而非错误答案；文案带 `判分失败` 前缀，避免误解 |
| D4 | 未在题卡中提供模型/知识库/附件工具栏 | 无 | 作答不需要这些控制，本次只对齐视觉语言 |

## 5. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 868, Failures: 0` 来自
`artifacts/verify-0028-quiz-answer-parity-20261109-165700.log`。
前端 `typecheck` / `build` 为本地命令执行结果，未计入 Java 用例总数。
