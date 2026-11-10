# 验证报告：0039-learning-stages-strategy

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0039-learning-stages-strategy` |
| 执行时间 | 2026-11-10 14:20–14:32（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 命令 | `.\scripts\verify.ps1 -Change 0039-learning-stages-strategy`；`npm run typecheck`；`npm run build` |

## 2. 变更摘要

- 新增 `MasteryPolicy.learningStage` / `learningStages`：按未作废作答与学习证据推导
  `not_yet_covered` / `explained` / `practiced_not_demonstrated` / `practiced_with_help` /
  `independently_demonstrated`。
- `mastery_status`（工具）与 REST 状态快照新增 `learning_stages`，覆盖全部知识点。
- `NextStep` 增加 `gate`（`qualitative` / `quantitative`）与 `forgetting_risk`；
  `answer_pending` 补齐知识点名称、类型、状态、门槛与掌握度。
- `nextObjective` 对齐参考实现优先级：待答 → 未延后的到期复习 → 第一个可选未掌握目标 →
  只剩延后目标时回退 → `complete`。
- 未触碰目标的动作由 `teaching_strategy` 决定：`teach_first` → `teach`，缺省 → `probe`；
  已有记录后按门槛返回 `assess` / `practice`。

## 3. 验证结果

```
[INFO] Tests run: 934, Failures: 0, Errors: 0, Skipped: 0
[PASS] 测试通过。
```

```
> tsc --noEmit            # 通过
> next build              # 通过，8 个路由生成
```

新增/更新用例：

| 用例 | 覆盖 |
|---|---|
| `MasteryPolicyTest`（新增 8 个） | 五个阶段判定、作废作答不计、证据计入、`learningStages` 覆盖全部目标 |
| `PolicyOrderingTest` | 到期复习优先、`probe`/`teach`、`practice`/`assess`、延后回退、延后不复习、`gate`/`forgetting_risk` |
| `PathStateTest` | 状态快照新增 `learning_stages` 键与取值 |
| `StatusAndQuizToolsTest` | `mastery_status` 返回 `learning_stages` |
| `ObjectiveStateTest` | 单目标延后后回退到该目标而不是 `complete` |

## 4. 行为变化

| 场景 | 变更前 | 变更后（DeepTutor 一致） |
|---|---|---|
| 有到期复习 + 有未掌握目标 | `next` 推进新目标 | `next` 返回 `review` |
| 新目标动作 | `practice` / `assess` | `probe`（或 `teach_first` 时 `teach`） |
| 已有作答的定量目标 | `practice` | `practice`（不变） |
| 已有记录的定性目标 | `assess` | `assess`（不变） |
| 全部剩余目标被延后 | `complete` | 回退到第一个延后目标 |
| 延后目标的到期复习 | 会被返回 | 被跳过 |
| `next` 字段 | 无 `gate` / `forgetting_risk` | 两者都有 |
| 状态快照 | 无逐目标阶段 | `learning_stages` |

## 5. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `current_stage`（诊断/讲解/练习/复习游标）未移植 | 中 | 只影响仪表盘展示，`next` 已由门槛决定；留到存储/展示批次 |
| D2 | `NextStep` 未带 `pending_question` 公共投影与 `session_id` | 低 | 状态快照顶层已有 `pending_question` |
| D3 | `nextObjective` 仍保留前置硬门控；DeepTutor 的 frontier 不检查前置 | 低 | 本仓库 INV-27 的历史约束，保持现状 |
| D4 | `learning_stage` 的证据不过滤 `voided`（与参考实现一致） | 低 | 与 DeepTutor 行为相同，未擅自修正 |
| D5 | 画像为空时仍未返回 `intake_instruction` 文案 | 低 | 提示词包已覆盖 intake 指引 |

## 6. 结论

- [x] 建议归档（等待人类确认）
- [ ] 需修复后复验
- [ ] 需人类决策
