# Proposal: 掌握判分回退到 DeepTutor 语义

## Why

真实对话里两道简答题被判错，导师却用自然语言说「你答对了」，随后 `repair(correct)` 回执又称「已纠正」。
排查参考实现后确认：`0036` 把掌握判分改成了与 DeepTutor 相反的一套，并在证据里写成「DeepTutor 一致」：

| 规则 | DeepTutor | 现状（0036 之后） |
|---|---|---|
| `accepted_answers` | 参与判分 | 忽略（注册时却强制要求） |
| 无法解析 | `ungraded`，零状态变更 | `incorrect`，写负证据 |
| 选择题写正文 | 可解析为标签 | 只比标签 |
| 简答相似度 | 0.90 + 编辑距离相似度 | 0.85 + SequenceMatcher |
| 开放题判对 | 服务层降 `partial`（不得单独授予掌握） | 保持 `correct` |

同时 `repair(correct)` 无论重判结果如何都回「已纠正」，模型据此对学习者撒谎。

## What Changes

- 删除 `MasteryGrading`，掌握路径改用与练习题卷相同的 `Grading.grade(PendingQuestion, String)`：
  `accepted_answers`、选项正文解析、数值容差、0.90 模糊阈值、`ungraded` 全部恢复。
- `LearningService.grade`：`ungraded` 直接返回、零状态变更；开放题 `correct` 降 `partial`。
- `StudyTools.mastery_grade`：`ungraded` 返回 `ok=false` + `Not graded: … Ask the learner to clarify.`。
- `POST /grade`：只把真正 `graded` 的结果写入会话消息；`ungraded` 走 422。
- `repair(correct)`：重判为 `ungraded` 时抛前置条件错误；返回体增加 `result`；
  `mastery_repair_question` 回执带真实判定。
- 删除 Java 独有的「简答题必须带 `accepted_answers`」注册约束（判分器已真正使用它）。

## Capabilities

### Modified Capabilities

- `learning-tools`：判分规则、`ungraded` 语义、开放题降级、修复回执与 REST 反馈落库。

### Removed Capabilities

- `learning-tools`：`Short Questions Need Accepted Answers`（注册期强制不再需要）。

## Impact

- Java：`MasteryGrading`（删除）、`LearningService`、`StudyTools`、`RepairTools`、`StudyController`。
- 测试：`MasteryGradingTest`（删除）、`GradingTest`/`AnswerFixtures`（fixture 单一期望）、
  `GradeFlowTest`、`RepairQuestionTest`、`MasteryGradeContractTest`、`StudyApiTest`、
  `StatusAndQuizToolsTest`、`ReviewSchedulingTest`。
- 行为：无法解析的作答不再记成错误；简答同义表述可判对；开放题不再单独授予掌握；
  修复失败会如实报错。
