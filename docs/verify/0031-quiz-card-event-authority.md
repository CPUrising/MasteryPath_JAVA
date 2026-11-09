# 验证报告：0031-quiz-card-event-authority

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0031-quiz-card-event-authority` |
| 执行时间 | 2026-11-09 21:36–21:50（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 工具链版本 | JDK 21.0.11 / Maven Wrapper 3.9.16 / Spring Boot 3.5.16 / Next.js 14.2.35 |
| 运行环境 | 本地；后端 `127.0.0.1:8081`，前端 `127.0.0.1:3001`，mock-llm `127.0.0.1:9099` |
| 命令 | `.\scripts\verify.ps1 -Change 0031-quiz-card-event-authority` |
| 回归日志 | `target/surefire-reports`、`artifacts/verify-0031-quiz-card-event-authority-*.log` |

> 本 change 不新增表、不改 DDL；`messages.data` 复用 `card` 键并新增 `grade` 键。

## 2. 变更摘要

- `question_card` 事件新增 `card_kind=mastery`、`turn_id`、`objective{id,name}`；7 键公开投影不变。
- `POST /learning/paths/{id}/grade` 接受 `session_id` / `turn_id`，把 user 答案与 assistant `data.grade`
  写入 `messages`；`already_graded` 不重复写。
- `ungraded` 保持 422 契约，但反馈落库为 `status=ungraded`，前端识别后允许重答。
- short 题注册要求 `accepted_answers`（纯数值/ASCII 单 token 豁免）。
- 前端：题卡内联消息流、历史 `data.card` 恢复、已作答卡收起输入区、`pending_question` 仅兜底。

## 3. 后端测试

全量结果：

```text
Tests run: 874, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
[PASS] 测试通过。
```

新增/修改的断言：

| 测试 | 覆盖 |
|---|---|
| `TurnRunnerTest#derivesQuestionCardFromToolResult` | `card_kind` / `turn_id` / `objective`；事件不含答案；assistant `data.card` 持久化 |
| `StudyApiTest#gradeFeedbackPersistsInSessionMessages` | user 答案 + assistant `data.grade` 落库，含 `question_id` |
| `StudyApiTest#ungradedGradePersistsWithoutMasteryChange` | 422 + `status=ungraded` + 掌握度/证据零变化 |
| `StudyApiTest#repeatedGradeDoesNotDuplicateMessages` | `already_graded` 不重复写消息 |
| `StudyApiTest#naturalLanguageShortNeedsAcceptedAnswers` | 自然语言 short 缺 `accepted_answers` → 422 |
| `StudyApiTest#numericShortIsAllowedWithoutAcceptedAnswers` | 数值 short 豁免 |
| `StudyApiTest#shortWithAcceptedAnswersIsAllowed` | 带 `accepted_answers` 的 short 放行 |

## 4. 前端验证

- `npm run typecheck`：通过。
- `npm run build`：通过（8 个路由全部生成）。
- browser_use 手工路径（mock-llm，非真实模型）：
  1. 打开 `/learning/mastery/p0031/sessions?session=s0031&mode=study`；
  2. 输入「出题」→ 出现含 A/B/C/D 的题卡；
  3. 点击 B → 出现「已作答」「回答正确」「解析」「已掌握」，进度 `1/2`；
  4. 刷新 → 仍为 1 张题卡、1 个已作答标记、1 个反馈块，进度保持 `1/2`；
  5. 网络日志确认 `POST /learning/paths/p0031/grade` 后由 `GET /learning/paths/p0031` 与
     `GET /learning/sessions/s0031` 恢复，说明持久化来自后端而非仅前端本地状态。
- 浏览器代理采集截图：`quiz-card-before-answer.png`、`quiz-card-after-answer-b.png`、
  `quiz-card-after-reload.png`（未复制进仓库）。

## 5. 回归

| 范围 | 结果 |
|---|---|
| 全量 `verify.ps1` | 874 / 0 / 0 |
| 定向 `TurnRunnerTest,StudyApiTest` | 通过（StudyApiTest 16 项） |
| 前端 typecheck / build | 通过 |

## 6. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `ungraded` 仍返回 422，而不是 200 | 一般 | 保持参考契约；前端识别 `ungraded` 并显示可重答提示 |
| D2 | short 强制 `accepted_answers` 可能让模型多一次重试 | 一般 | 数值/ASCII 单 token 豁免；错误文案给出明确补充指引 |
| D3 | 前端手测使用 mock-llm，不是真实模型 | 一般 | 真实模型全链路 E2E 按计划放在 `0033` 的 Verify |
| D4 | mock 剧本的题面（`synchronized`）与当前知识点（HTTP cache headers）不匹配 | 低 | 属 mock 数据问题，不是后端缺陷；已在本报告登记 |
| D5 | 历史 `data.grade` 是服务层结果，不含 `ok/content` 字段 | 低 | 前端只读 `status/result/explanation/gate_cleared`；类型上 `ok/content` 仅用于 REST 即时响应 |

## 7. 结论

- [x] 建议归档（等待人类确认）
- [ ] 需修复后复验
- [ ] 需人类决策
