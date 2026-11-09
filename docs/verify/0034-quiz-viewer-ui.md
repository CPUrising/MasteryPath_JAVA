# 验证报告：0034-quiz-viewer-ui

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0034-quiz-viewer-ui` |
| 执行时间 | 2026-11-09 23:00–00:05（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 工具链版本 | Next.js 14.2.35 / TypeScript 5 / JDK 21.0.11 / Node 24.18 |
| 命令 | `npm run typecheck`、`npm run build`、`.\scripts\verify.ps1 -Change 0034-quiz-viewer-ui` |

## 2. 变更摘要

- `lib/types.ts`：新增 `QuizQuestion` / `QuizSet` / `QuizPlan` / `QuizSetSubmitOutcome`；
  `SessionMessage.data` 增加 `quiz_set`。
- `lib/api.ts`：新增 `getQuizSet` / `submitQuizSet`。
- `lib/ws.ts`：`TurnRequest` 增加 `capability` / `num_questions` / `difficulty`。
- `components/learning/QuizViewer.tsx`：作答、整卷提交、`ungraded` 提示、提交后复盘与总分。
- `components/common/MessageComposer.tsx`：能力芯片从静态标签扩展为可切换（传 `capabilities` 时）。
- 掌握之路会话页：能力切换、`quiz_plan` / `quiz_question` / `quiz_set` 事件消费、
  历史 `data.quiz_set` 恢复。
- `scripts/mock-llm/mock-llm.mjs`：增加 `deep_question` 生成剧本（仅开发工具）。

## 3. 前端检查

- `npm run typecheck`：通过。
- `npm run build`：通过（8 个路由全部生成，会话页 7.46 kB）。
- browser_use（Mock LLM，后端 8082 / 前端 3002）：
  - 点击「练习题」→ 发送后收到 `Practice: …` 题卷，3 题（选择/短答/开放）内联渲染；
  - 回合结束后能力自动回到「掌握辅导」；
  - 作答（B / 核心思想 / 应用场景）后「提交题卷」由禁用变为可点；
  - 提交后显示「得分 100%」、逐题「你的作答 / 正确答案 / 解析」，提交按钮消失；
  - 刷新后题卷与判分结果完整恢复。

## 4. 真实模型 E2E

环境：临时工作区 `target/tmp-0034-real`，`settings.json` 复制自项目 `data/`，
使用项目已存 API Key 与 active 模型 `deepseek-v4.1-flash`（Key 未出现在任何输出中）；
后端 `127.0.0.1:8083`。扮演真实用户经产品 WS/REST 输入自然语言。

| 阶段 | 用户输入 / 动作 | 结果 |
|---|---|---|
| outline | 「只保留 1 个模块、1 个知识点，类型必须是 memory，内容是 Java 线程六种状态名称」 | `mastery_build` 建立 1 模块 1 知识点，出现 1 个 source gap |
| study | 接受 gap、切 study、「按默认来，直接出第一道选择题」 | 模型记录画像并登记选择题卡片 |
| ungraded | 作答「我不确定」 | HTTP 422，返回 `Not graded: could not tell which option…`，掌握度与槽位不变 |
| incorrect | 同一题改答「B」 | `result=incorrect`，掌握度 0.0 → 0.0 |
| correct | 新题答「A」 | `result=correct`，掌握度 0.0 → 0.625 |
| partial | 开放题只写 4 个状态名 | `result=partial`，掌握度 0.625 → 0.306 |
| complete mastery | 连续 3 道正确（A / 全六态短答 / B） | 掌握度 0.306 → 0.625 → 0.788 → 0.927，`gate_cleared=true`，`counts.mastered=1`，`next=complete` |
| deep_question | 「请针对「Java 线程的六种状态名称」生成 3 道练习题」 | `quiz_plan` → 3 条 `quiz_question` → `quiz_set`（set `627419e0…`） |
| submit | C / 六态短答 / BLOCKED 与 WAITING 区别 | `status=submitted`，`score=0.667`，开放题 `partial`，答案与解析释放 |
| review | 切 review 模式请求检查 | 模型报告「路径已全部通关、待复习队列为空」 |

真实模型下四种判分结果（`ungraded` / `incorrect` / `correct` / `partial`）均实际出现，
题卷生成、提交与复盘、review 收尾均走通。

## 5. 回归

| 范围 | 结果 |
|---|---|
| 全量 `verify.ps1` | 893 / 0 / 0 |
| 前端 typecheck / build | 通过 |
| 后端 | 无代码改动 |

## 6. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `deep_question` 生成只看用户消息（`topic`），不含路径/知识点上下文。首次只说「针对这个知识点生成 3 道练习题」时，真实模型生成了勾股定理题 | 中 | 本次在消息中显式写出主题后生成正确题卷；建议后续 change 把 `path` / `next` 目标注入生成提示 |
| D2 | 一次真实模型回合把题目写在正文里、未调用 `mastery_quiz` 登记题卡 | 低 | 用户再次要求「用出题工具登记」后正常；属模型行为波动 |
| D3 | 概念型（concept）知识点按策略不出选择题，无法用于演示四种判分结果 | 低 | E2E 改用 memory 型路径完成判分演示；concept 路径本身也走通了 outline/study |
| D4 | 用 PowerShell `Invoke-RestMethod` 创建路径时中文 goal 被写成乱码 | 低 | 改用 `curl.exe` + UTF-8 文件创建 `e2e-quiz`；属验证编排问题，不是产品缺陷 |
| D5 | Mock LLM 原先不识别 `deep_question`，前端手测会得到生成失败 | 低 | 为 mock 增加练习题 JSON 剧本（仅开发工具，不进入自动化测试） |

## 7. 结论

- [x] 建议归档（等待人类确认）
- [ ] 需修复后复验
- [ ] 需人类决策
