# 验证报告：0036-mastery-grading-parity

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0036-mastery-grading-parity` |
| 执行时间 | 2026-11-10 13:10–13:35（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 命令 | `.\mvnw.cmd -q -Dtest=... test`、`.\scripts\verify.ps1 -Change 0036-mastery-grading-parity` |

## 2. 变更摘要

- 新增 `MasteryGrading`，逐字复刻 DeepTutor `learning/grading.py::grade_answer`：
  - `expected_answer` 为空 → 判错（fail-closed）；
  - `choice` 去空格整串比较；`short` 精确或 ≤30 字时 `SequenceMatcher` ≥0.85；
  - `open` 按 `[,;，；。\n]` 拆词、命中率 ≥0.6；
  - 只返回布尔，不再有 `partial` / `ungraded` / `accepted_answers` / 数值容差。
- `MasteryScore` 改为 DeepTutor `compute_mastery`：
  - 最近 5 次权重 `(0.5,0.7,0.85,0.95,1.0)`；
  - 置信上限 `{1:0.5, 2:0.8}`；
  - 只统计 `independent && !voided`。
- `LearningService.grade` / `repairQuestion` 改用 `MasteryGrading`；移除
  「提示降级」「开放题 correct→partial」与 `ungraded` 分支。提示只影响
  `independent`，不再改变结果。

## 3. 验证结果

```
[INFO] Tests run: 905, Failures: 0, Errors: 0, Skipped: 0
[PASS] 测试通过。
```

新增/重写用例：

| 用例 | 覆盖 |
|---|---|
| `MasteryGradingTest`（9 个） | 空期望、choice 归一、short 0.85 与 30 字边界、open 关键词、未知题型、matching-blocks 比例 |
| `MasteryScoreTest`（11 个） | 1 次/2 次置信上限、3 次达 1.0、最近 5 次权重、作废与非独立过滤、恢复曲线 |
| `GradeFlowTest` | 无法解析→incorrect、提示不降级、开放题判对但受上限、三次正确才清门槛 |
| `PathStateTest` / `ReviewSchedulingTest` / `RepairQuestionTest` / `StudyApiTest` / `StatusAndQuizToolsTest` | 旧「一次答对即掌握」假设全部改为三次正确；修复/复习链路同步 |

## 4. 行为变化

| 场景 | 变更前 | 变更后（DeepTutor 一致） |
|---|---|---|
| 选择题作答 `zzz` | `ungraded` + 零变更 | `incorrect`，正常写 attempt/证据 |
| 短答相似度 | Levenshtein ≥0.90 + accepted_answers | SequenceMatcher ≥0.85，无 accepted_answers |
| 开放题全命中关键词 | 服务层降级为 `partial` | `correct` |
| 使用提示 | 结果降级为 `partial` | 结果不变，`independent=false` 且不计入掌握度 |
| 一次答对 | 可清门槛（分数 1.0） | 分数 0.5，不能清门槛 |
| 三次答对 | — | 分数 1.0，清门槛并播种复习 |

## 5. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `Decision` 仍保留 `partial/ungraded` 常量，练习题卷继续使用旧 `Grading` | 低 | 掌握判分不再产生它们；题卷不在本批次范围 |
| D2 | DeepTutor 的错误分类（`metacognitive` / `application_error`）尚未移植 | 中 | 留到「题卡/证据」批次 |
| D3 | 证据质量分与 `error_type` 字段未完全对齐 | 中 | 同上 |
| D4 | 旧数据中已有 `partial` 证据与旧掌握度保留原值 | 低 | 新判分只写 `correct/incorrect`；重算时按新权重 |
| D5 | 前端 `ungraded` 分支变为不可达 | 低 | 下一批次清理；不影响当前构建 |

## 6. 结论

- [x] 建议归档（等待人类确认）
- [ ] 需修复后复验
- [ ] 需人类决策
