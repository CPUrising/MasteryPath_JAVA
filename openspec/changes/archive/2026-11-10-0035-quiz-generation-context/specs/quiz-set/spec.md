# quiz-set Specification

## ADDED Requirements

### Requirement: Generation Uses Path Context

`deep_question` 生成提示 MUST 包含学习路径的名称与目标；当路径已有模块或知识点时，
MUST 同时包含其名称（必要时附类型）。题目 MUST 面向这些知识点，用户消息只用于确定练习主题。
上下文 MUST NOT 含答案、判分依据、掌握度或证据。

#### Scenario: Prompt carries path and knowledge points

- **GIVEN** 一条名为「线程状态记忆」、目标为「记住 Java 线程的六种状态」的路径，含 1 个知识点
- **WHEN** 发起 `deep_question` 回合
- **THEN** 生成提示 MUST 含路径名、目标与该知识点名称

#### Scenario: Requests without an explicit topic still stay on-path

- **GIVEN** 用户只输入「针对这个知识点生成 3 道练习题」
- **WHEN** 生成题卷
- **THEN** 题目主题 MUST 来自路径上下文中的知识点，而不是无关领域

#### Scenario: Context never leaks grading data

- **GIVEN** 任意路径状态
- **WHEN** 构造生成上下文
- **THEN** 上下文 MUST NOT 含 `expected_answer`、`accepted_answers`、掌握度或学习证据
