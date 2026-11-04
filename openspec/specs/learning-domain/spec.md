# Learning Domain Capability Spec

## Purpose

定义 MasteryPath 的领域模型、状态派生与掌握门槛判定：什么算「掌握」、什么时候可以推进到下一个知识点。该能力是纯函数层，不含 I/O 与 LLM，是其他所有能力判定对错的基准。

## Requirements

### Requirement: Typed Knowledge Point

系统 SHALL 用四类知识点（`memory` / `concept` / `procedure` / `design`）表达目标，并 MUST 拒绝未知类型；读取历史中文类型值时 SHALL 映射为对应英文枚举。已存在的知识点 `type` MUST 为只读。

#### Scenario: TC-A1 name and type validation

- **GIVEN** 构造一个知识点
- **WHEN** `name` 去空白后少于 2 字符或缺少 `type`
- **THEN** 系统 MUST 抛出校验错误

#### Scenario: TC-A2 reject unknown type

- **GIVEN** 输入 `type="foo"`
- **WHEN** 反序列化
- **THEN** 系统 MUST 拒绝，且 MUST NOT 静默映射为任一已知类型

#### Scenario: TC-A3 map legacy Chinese type

- **GIVEN** 输入 `type="概念型"`
- **WHEN** 反序列化
- **THEN** 系统 MUST 得到 `concept`

#### Scenario: TC-A4 ignore unknown fields

- **GIVEN** 一段含未声明字段的历史 JSON
- **WHEN** 反序列化为聚合
- **THEN** 系统 MUST 忽略未知字段并成功构造

### Requirement: Mastery Gate

系统 SHALL 按知识点类型分支判定掌握：`memory` / `procedure` 由量化分数 `mastery_levels ≥ 0.9` 判定；`concept` / `design` 由定性通过判定，量化分 MUST NOT 参与。学习者声明（override）SHALL 使其掌握但 MUST NOT 写入分数，也 MUST NOT 产生学习证据。

#### Scenario: TC-C1 TC-C2 quantitative gate boundary

- **GIVEN** 一个 `memory` 知识点
- **WHEN** `mastery_levels` 分别为 `0.85` 与 `0.90`
- **THEN** 前者 MUST 判为未掌握，后者 MUST 判为已掌握

#### Scenario: TC-C4 TC-C5 qualitative gate

- **GIVEN** 一个 `concept` 知识点
- **WHEN** 量化分为 `1.0` 但无定性通过 / 定性通过为真
- **THEN** 前者 MUST 判为未掌握，后者 MUST 判为已掌握

#### Scenario: TC-C6 TC-C7 TC-C8 learner declaration

- **GIVEN** 某知识点存在学习者 override
- **WHEN** 走完声明流程
- **THEN** `is_mastered` MUST 为真且来源 MUST 为 `learner`
- **AND** `mastery_levels` MUST 未被写入
- **AND** `learning_evidence` MUST 无新增

#### Scenario: TC-C12 voided attempt ignored

- **GIVEN** 一个 `voided=true` 的作答记录
- **WHEN** 计算掌握度与证据
- **THEN** 该记录 MUST NOT 计入

### Requirement: Frontier Advancement

系统 SHALL 以「已掌握」为唯一推进依据计算下一个目标，并 MUST 把未满足的前置知识点作为硬门控：前置未掌握时该目标 MUST NOT 被选中，也 MUST NOT 可被评估。模块级 `display_target` MUST NOT 参与推进。

#### Scenario: TC-C9 TC-C10 next objective

- **GIVEN** 模块与知识点顺序固定
- **WHEN** 存在未掌握且可选的目标 / 全部已掌握或已延后
- **THEN** 系统 MUST 返回第一个可选目标 / MUST 返回 `complete`

#### Scenario: TC-C11 answer pending first

- **GIVEN** 存在未作答的待答题目
- **WHEN** 计算下一个目标
- **THEN** 系统 MUST 返回 `answer_pending`

#### Scenario: TC-F1 display target is not a gate

- **GIVEN** 模块聚合分低于 `display_target` 但所有知识点已掌握
- **WHEN** 计算下一个目标
- **THEN** 系统 MUST 返回 `complete`

#### Scenario: TC-F6 TC-F7 TC-F8 prerequisite hard gate

- **GIVEN** `design` 知识点存在未掌握的前置 `procedure`
- **WHEN** 计算下一个目标
- **THEN** 系统 MUST 返回该前置而不是 `design`
- **AND** 当前置通过但定性未通过时 `design` MUST 仍未掌握
- **AND** 当前置通过且定性通过时 `design` MUST 掌握

### Requirement: Goal Preservation

系统 SHALL 在除显式编辑之外的所有地图操作中保留路径目标 `goal`，MUST NOT 因重建或修订而清空。

#### Scenario: TC-C17 goal survives rebuild

- **GIVEN** 路径具有非空 `goal`
- **WHEN** 执行一次 build 或 revise
- **THEN** `goal` MUST 保持不变
