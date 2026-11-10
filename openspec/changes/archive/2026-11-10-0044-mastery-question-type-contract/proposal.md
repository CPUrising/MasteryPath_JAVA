# Proposal: 简答题长度契约

## Why

`0043` 把判分器对齐回 DeepTutor 之后，`short` 的规则是：归一化后期望答案**超过 30 字只做精确匹配**。
但出题侧没有任何约束，模型仍然会用 `short` 出「写出子句并说明理由」这类长答题——
学习者写得完全正确也只能被判 `incorrect`，因为没有任何相似度能匹配上。

这类假阴性不是判分 bug，而是**题型与答案形态不匹配**：解释型作答应该用 `open` + 关键词，
或走 `mastery_assess` 定性评估。

## What Changes

- `mastery_quiz` 注册时校验：`question_type=short` 且归一化后的 `expected_answer` 超过 30 字时，
  MUST 至少有一条归一化后 ≤30 字的 `accepted_answers`；否则拒绝并提示改用 `open` 或补充短等价答案。
- `mastery_quiz` 的工具描述补一句题型指引：`short` 只用于 ≤30 字的短答。

## Capabilities

### Modified Capabilities

- `learning-tools`：新增「简答题长度契约」要求。

## Impact

- Java：`StudyTools`（注册校验与工具描述）。
- 测试：`StatusAndQuizToolsTest`、`StudyApiTest`。
- 行为：长答案短答题会在出题时被拒，而不是让学习者答完被判错；`open` / 带短等价答案的 `short` 不受影响。
