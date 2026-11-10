# Design: 掌握判分回退到 DeepTutor 语义

## 1. 单一判分器

`Grading` 已经逐条实现了参考实现 `learning/grading.py`：

| 步骤 | 行为 |
|---|---|
| 归一化 | NFKC → 小写 → 折叠空白 → 剥首尾标点 → 含 CJK 去空格 |
| `choice` | 标签命中；否则选项正文归一化后唯一匹配；解析不出 → `ungraded` |
| `short` | 精确 → `accepted_answers` → 数值容差 1e-6 → 相似度 ≥0.90 → >30 字只精确 |
| `open` | required 全中 + optional 命中率 ≥0.6；缺 required 但有命中 → `partial` |
| 空作答 / 无关键词 | `ungraded`，原因可读 |

因此删除 `MasteryGrading`（0.85 + SequenceMatcher + 忽略 accepted_answers + fail-closed），
掌握路径直接调用 `Grading.grade(pending, answer)`。两条链路从此只有一个判分实现。

## 2. 服务层

`LearningService.grade`：

```
decision = Grading.grade(interaction.getQuestion(), answer)
if decision.isUngraded():  return {status: "ungraded", reason}
… 事务内 …
result = decision.result()
if question_type == open and result == correct: result = partial   // INV-35
is_correct = result == correct
```

`ungraded` 在进入事务前就返回，因此不写 attempt / evidence、不动掌握度、不清
`pending_question`、不重置 `consecutiveQuestions`（与 `INV-46` 一致）。

提示（`hints_used > 0`）仍只写 `independent=false`、不降级——这是本 change 明确保留的既有行为。

## 3. 工具与 REST

- `mastery_grade`：`status == ungraded` → `ToolResult.failure("Not graded: <reason>. Ask the learner to clarify.", metadata)`。
  其它情况保持 `Graded: <result>.`，metadata 里带 `correct_label` / `correct_body`。
- `POST /learning/paths/{id}/grade`：只有 `status == graded` 才调用 `recordGradeInteraction`；
  `ungraded` 由 422 返回，不写会话消息（参考实现同样不写）。

## 4. 修复

`repairQuestion(correct)`：

```
question.expectedAnswer = newExpected
decision = Grading.grade(question, userAnswer)
require(!decision.isUngraded(), "corrected answer cannot be graded: " + reason)
result = decision.result()
if question_type == open and result == correct: result = partial
```

若新答案键仍无法判定，修复直接失败——模型不会再收到「已纠正」的假信号。

返回体增加 `result`（`void` 时为 `"voided"`），事件 payload 不变；
`mastery_repair_question` 回执改为：

- `void` → `The question was voided; continue from mastery_status.next.`
- `correct` → `The answer key was corrected; the stored reply now grades <result>.`

## 5. 测试影响

- 删除 `MasteryGradingTest`；`real-answers-13.json` 去掉 `mastery` 列（两条链路同一函数）。
- `GradeFlowTest`：`zzz` → `ungraded` 零变更；开放题 `correct` → `partial`。
- `RepairQuestionTest`：`correct` 后仍判错的用例改为「可解析但错误」的作答（`A` 对 `B`）；
  返回值键数 8 → 9；新增「重判 `ungraded` 时抛错」。
- `MasteryGradeContractTest`：`unparseableChoiceIsIncorrectNotUngraded` →
  `unparseableChoiceIsUngraded`（422、零变更）。
- `StudyApiTest`：无法解析的作答改为 422 且不落消息。

## 6. 兼容与风险

- 已经写成 `incorrect` 的历史 attempt / evidence 不追溯重判；只有新作答按新规则。
- `MasteryScore` 仍只统计 `correct`；`ungraded` 不产生记录，掌握度曲线回到「没答过」。
- 前端 `q.ungraded` 文案从此真正可达。
- 简答题注册不再强制 `accepted_answers`，模型需要自行提供才有等价判分。
