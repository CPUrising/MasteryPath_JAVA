# Proposal: 练习题生成注入路径上下文

## Why

`0034` 的真实模型 E2E 发现：`deep_question` 生成器只看用户消息（作为 `topic`），
不知道学习路径、目标和当前知识点。用户只说「针对这个知识点生成 3 道练习题」时，
真实模型生成了与路径无关的勾股定理题。这是可用性缺陷，不是模型随机性。

## What Changes

- `QuizSetTurn` 从 `LearningProgress` 构造只读上下文：路径名、目标、模块与知识点名称/类型。
- `QuizSetGenerator` 的用户提示追加 `Learning context` 段，要求题目面向路径中的知识点。
- 公开投影、存储、事件顺序与判分语义不变；上下文不含答案、证据或掌握度。

## Capabilities

### Modified Capabilities

- `quiz-set`: 生成必须使用路径上下文，而不仅是用户消息。

## Impact

- Java：`adapter/turn/QuizSetTurn`、`adapter/turn/QuizSetGenerator`。
- 测试：`QuizSetTurnTest` 断言生成提示包含路径名、目标与知识点。
- 不影响：前端、存储结构、REST、掌握闭环。
