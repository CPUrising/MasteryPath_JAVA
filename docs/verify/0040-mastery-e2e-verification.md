# 验证报告：0040-mastery-e2e-verification

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0040-mastery-e2e-verification` |
| 执行时间 | 2026-11-10 15:07–15:12（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 默认回归 | `.\scripts\verify.ps1 -Change 0040-mastery-e2e-verification` |
| 真实模型 E2E | `.\scripts\e2e-mastery.ps1`（单次，未重试） |
| 模型配置 | 本机 `data/settings.json`；报告不记录提供商与 Key |

## 2. 变更摘要

- 新增 `fixtures/real-answers-13.json` 与 `testsupport/AnswerFixtures`：13 条脱敏改写的真实风格作答。
- `GradingTest` / `MasteryGradingTest` 各增加一个 fixture 驱动用例，固定两条判分链路的差异。
- 新增 `MasteryGradeContractTest`：`POST /grade`、`GET session`、`GET mastery` 的字段与落库。
- 新增 `MasteryJourneyE2ETest`：真实模型、真实 HTTP/WebSocket、真实 SQLite，走完整条掌握之路。
- `verify.ps1` 默认排除 `e2e`（`all` / `unit`），新增 `-Scope e2e`；新增 `e2e-mastery.ps1`。

## 3. 默认回归

```
command: mvnw.cmd -B -ntp test -DexcludedGroups=e2e
[INFO] Tests run: 941, Failures: 0, Errors: 0, Skipped: 0
[PASS] 测试通过。
```

新增用例（+7）：

| 用例 | 覆盖 |
|---|---|
| `GradingTest#realAnswersKeepPracticeSemantics` | 13 条在练习题卷的 correct/partial/ungraded |
| `MasteryGradingTest#realAnswersKeepMasterySemantics` | 13 条在掌握路径的 correct/incorrect |
| `MasteryGradeContractTest`（5 个） | 首次正确 0.5、三次清门槛、无法解析判 incorrect、反馈落库、掌握度端点 |

## 4. 真实模型 E2E

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 132.4 s
[PASS] 真实模型掌握之路完成。
```

会话时间线与断言结果：

| 观察项 | 结果 |
|---|---|
| 大纲 | 第 1 轮由真实模型调用 `mastery_build` 建成，**未使用兜底** |
| 学习回合 | 6 轮 k1 出题/作答 + 5 轮 k2 讲解 + 1 轮 k2 评估，共 11 轮 |
| 判分次数 | 7 次（1 次刻意答错 + 6 次答对） |
| 判分结果 | 全部 `correct` / `incorrect`，**没有 `ungraded`** |
| 门槛 | k1 第 5 次正确时 `mastery_after=1.0`、`gate_cleared=true` |
| 定性目标 | k2 在会话内完成 `mastery_assess`，未走 REST 兜底 |
| 终态 | `next.action=complete`；`learning_stages` 覆盖两个目标；复习队列已播种 |
| 交谈留痕 | `target/e2e-artifacts/mastery-journey-20261110-150924.jsonl` 与 `.session.json` |

产物（gitignored）：

```
target/e2e-artifacts/
  mastery-journey-20261110-150924.jsonl         # 每轮学习者发言 + 全部 WS 帧 + 判分汇总
  mastery-journey-20261110-150924.session.json  # GET /learning/sessions/{id} 的完整内容
```

## 5. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | WS 回合不把 `data.grade` 写进会话消息（只有 REST `/grade` 会） | 中 | E2E 从 WS 帧 transcript 断言判分，会话消息只断言成对持久化；已记录 |
| D2 | 自动化作答从本地库读取 `expected_answer` 后提交 | 中 | 真实模型题目不可预知；这是夹具手段，产品接口不暴露答案键 |
| D3 | E2E 不进默认回归，必须显式运行脚本 | 低 | 网络、费用、非确定性；`e2e` 标签 + 环境变量双保险 |
| D4 | 首次正确 `mastery_after=0.5128`（非 0.5） | 低 | 先前的错误作答仍在最近窗口内；与 0036 算法一致，不是缺陷 |
| D5 | 计划预估 942，实际 941 | 低 | 契约 5 + fixture 2 = 7，计划多加 1；以实际为准 |
| D6 | 模型在一轮内连续调用多个工具（build → mode → quiz） | 低 | 回合协议允许；E2E 只断言 `question_card` 与判分顺序 |

## 6. 结论

- [x] 建议归档（等待人类确认）
- [ ] 需修复后复验
- [ ] 需人类决策
