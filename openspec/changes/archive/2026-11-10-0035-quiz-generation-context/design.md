# Design: 练习题生成注入路径上下文

## 1. 上下文来源

`QuizSetTurn` 已经加载 `LearningProgress`，它提供：

- `name`、`goal`；
- `modules[]`：每个模块的 `name` 与 `knowledgePoints[]`（`name`、`type`）。

构造一段有界文本（最多 12 个知识点，避免提示膨胀）：

```
Path: <name>
Goal: <goal>
Modules and knowledge points:
- <module name>: <kp1 name> [<type>], <kp2 name> [<type>]
...
Focus on these knowledge points; the learner's request below only sets the practice topic.
```

不包含：答案、`learning_evidence`、掌握度、待答题、会话原文。

## 2. 提示组装

`QuizSetGenerator.generate(spec, setId, context)`：

```
Topic: <用户消息>
Number of questions: N
Allowed question types: ...
Difficulty: ...
Learning context:
<context>
Return exactly N questions.
```

系统提示同步强调「questions must be about the learning context」。

## 3. 兼容与边界

- 上下文为空（理论只在路径无名称/目标时）时退回原提示，不阻断生成。
- 题数、题型、难度、修复与失败零落库语义全部不变。
- `knowledge_point_id` 仍取模型返回值；为空不回填，避免把练习卷混入掌握证据。

## 4. 测试

- `QuizSetTurnTest#promptIncludesPathContext`：FakeLlm 捕获首个请求，断言用户提示包含
  `Path:`、`Goal:` 与知识点名称。
- 既有事件顺序、失败零落库、修复一次用例保持通过。
- 全量 `verify.ps1` 回归。
