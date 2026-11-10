# learning-tools Specification

## ADDED Requirements

### Requirement: Shuffled Choice Options And Card Free Text

选择题注册时，系统 MUST 打乱选项顺序并按位置重新贴标签（A/B/C…），
`expected_answer` MUST 跟随正确选项正文改到新标签。题卡 MUST 带 `allow_free_text=true`。
判分结果 MUST 含 `correct_label`、`correct_body`、`learner_answer` 与 `independent`。

#### Scenario: Correct body keeps its label

- **GIVEN** 模型以 A/B/C 顺序提交选项，正确项正文为 `body-2`
- **WHEN** 注册题目
- **THEN** 卡片标签 MUST 为连续的 A/B/C，且 `expected_answer` MUST 指向 `body-2` 的新标签

#### Scenario: Card allows free text

- **GIVEN** 任一掌握题卡
- **WHEN** 学习者读取卡片
- **THEN** 卡片 MUST 含 `allow_free_text=true`

#### Scenario: Grade result carries the answer key

- **GIVEN** 一道选择题被判分
- **WHEN** 读取 `mastery_grade` 结果
- **THEN** 结果 MUST 含 `correct_label`、`correct_body`、`learner_answer` 与 `independent`

### Requirement: Optional Question Id And Argument-less Skip

`mastery_grade` SHALL 允许省略 `question_id`，缺省 MUST 判当前待答题；
`mastery_skip_question` MUST 无参数，缺省 MUST 跳过当前待答题。

#### Scenario: Grade without question id

- **GIVEN** 一道待答题
- **WHEN** 调用 `mastery_grade` 只传 `answer`
- **THEN** 系统 MUST 判该待答题

#### Scenario: Skip without arguments

- **GIVEN** 一道待答题
- **WHEN** 调用 `mastery_skip_question` 不带参数
- **THEN** 系统 MUST 清空该待答题
