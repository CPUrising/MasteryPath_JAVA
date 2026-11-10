# Design: 定量目标的题型门控

## 1. 门控放在注册入口，而不是判分器

判分器的既有语义不变：`open` 判对仍然在服务层降为 `partial`（`INV-35`）。问题是**题本身
不可赢**，因此约束应落在出题入口：

```
mastery_quiz 注册前：
  MasteryPolicy.gateKind(kp) == "quantitative"   // memory / procedure
  AND spec.questionType() == "open"
  → 拒绝，不登记 interaction，不结束回合以外的状态
```

- 只在工具层（`StudyTools.Quiz`）校验。直接调 `LearningService.registerQuestion` 的测试与
  历史数据不受影响；0044 的简答长度契约同样落在这一层，保持一致。
- 判定复用 `MasteryPolicy.gateKind`，不新造类型集合——门槛种类只有一个权威来源。
- `concept` / `design` 仍允许 open：它们的掌握由 `mastery_assess` 决定，open 题不参与定量分数。
- `QuizSpec.QUESTION_TYPES` 只有 `choice` / `short` / `open`，没有 `code`；不新增题型。

拒绝文案（面向模型，固定字符串）：

```
open questions cannot earn mastery credit for a memory or procedure objective: a correct
reply is recorded as partial and never raises the score. Use question_type=choice, or
question_type=short with an expected answer of 30 characters or fewer (add accepted_answers
for equivalent phrasings). Open objectives are opened by mastery_assess, not by a quiz.
```

## 2. 工具描述与导师可见反馈

`mastery-prompts` 规定提示词包必须逐字来自 DeepTutor 的 `mastery_loop.yaml`，因此**不改
YAML**。面向模型的约束只放在两处：

- `mastery_quiz` 的 tool description：给出「定量目标只能 choice/short」的规则。
- `ToolSchemas` 中 `question_type` 参数的描述：一句话说明 open 不产生掌握度。

## 3. repair 的诚实化

`action=correct` 的返回值新增：

- `effective`：本次修复是否可能改变掌握度。`void` 恒为 `true`；`correct` 在
  `question_type=open` 时为 `false`（重判结果必然是 `partial`，`is_correct=false`）。
- `effective=false` 时附 `credit_note`，说明「答案键与证据已更新，但 open 题判对仍记
  `partial`，掌握度不变」。

交互记录的 `result` 同步带上这两个字段，保证 `mastery_grade` 的幂等重放看到同样的结论。
`RepairTools` 的成功文案按 `effective` 分流，不再统一说「the stored reply now grades …」
让人误以为分数已恢复。

不在这里同步关键词：那会改动 `QuizSpec` 注册期固定的参照物，属于单独的行为增量；本
change 只保证「不再骗人」。

## 4. 不动的部分

- `Grading.gradeOpen` 的关键词比例、`MasteryScore` 的权重与 0.5 / 0.8 置信上限、
  `partial` 计 0 的语义：全部保持，与参考实现一致。
- 历史 open 题不迁移、不删除，只允许通过 `repair` 作废或改键。

## 5. 运维数据修复

通过 `LearningService.repairQuestion(action=void)` 处理，而不是直接写 SQLite：保留
`version` 递增、`assessment.voided` 事件与审计链。作废对象：

| question_id | 原因 |
|---|---|
| `03ae518697fd4631a9ed8f96453cdba4` | open 题误用于 procedure 目标，partial 不代表理解程度 |
| `524e64ef9aa94fc29b9de9864a52c603` | 同上；答案键修正后仍只能是 partial |
| `81a23491d8c1444da862e74c92ee5a0f` | 待答 open 题，题型与目标不匹配，改出 choice |

作废后 `k5` 只剩两条 `choice` 正确作答，按置信上限为 `0.8`（仍低于 0.9）；后续由
`choice` / `short` 题继续累积。

## 6. 测试

- `StatusAndQuizToolsTest`：memory 目标 open 被拒；procedure 目标 open 被拒；同目标
  choice / short 正常注册；concept 目标 open 仍可注册。
- `RepairQuestionTest`：open 题 correct → `result=partial`、`effective=false`、掌握度不变；
  short 题 correct → `effective=true`。
