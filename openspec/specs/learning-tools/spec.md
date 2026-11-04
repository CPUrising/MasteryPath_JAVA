# Learning Tools Capability Spec

## Purpose

定义导师侧工具的行为合同：大纲生成与编辑、出题、确定性判分、定性评估、题目修复、捕获-延后与缺口处理。这些工具的入参 schema 即回合契约，前端与模型都依赖它，因此字段名与语义不得漂移。

## Requirements

### Requirement: Build And Revise Are Different Operations

系统 SHALL 把 `mastery_build` 实现为整图替换、`mastery_revise` 实现为按 id 的差分操作。非空路径的 `build` MUST 要求显式 `confirm`，否则 MUST 返回拒绝且地图与进度不变；`revise` MUST 保留未触动知识点的进度，且 MUST NOT 修改已存在知识点的 `type`。

#### Scenario: TC-J1 TC-J3 build validation

- **GIVEN** 空路径与合法入参 / 两个同名知识点
- **WHEN** 执行 `build`
- **THEN** 前者 MUST 建出与入参一致的模块与知识点并发出 `outline.built`，后者 MUST 拒绝

#### Scenario: TC-J2 rebuild requires confirm

- **GIVEN** 非空路径且未提供 `confirm`
- **WHEN** 执行 `build`
- **THEN** 系统 MUST 返回 `refused_rebuild` 并列出将丢失的内容，地图与进度 MUST 不变

#### Scenario: TC-J4 TC-J5 revise keeps progress

- **GIVEN** 已有带进度的地图
- **WHEN** 增删或移动知识点 / 尝试修改 `type`
- **THEN** 前者未触动知识点的 `mastery_levels` MUST 不变并发出 `outline.revised`，后者 MUST 拒绝

#### Scenario: TC-J6 gaps reported

- **GIVEN** 知识点无来源支撑
- **WHEN** 执行 `build`
- **THEN** 系统 MUST 返回非空缺口列表

### Requirement: Coverage Report On Generation

系统 SHALL 在 `build` / `revise` 后生成并保存覆盖报告（来源总数、已覆盖数、缺失来源、是否声明了材料、接地比、前置是否无环），MUST 可通过状态快照与独立端点读取。当模型未声明材料时 MUST NOT 把未用来源列为缺失。

#### Scenario: TC-N1 TC-N3 coverage persistence

- **GIVEN** 一次 `build`
- **WHEN** 读取覆盖报告
- **THEN** 字段 MUST 齐全且可读
- **AND** 未声明材料时缺失列表 MUST NOT 展开

#### Scenario: TC-N2 TC-N4 deterministic metrics

- **GIVEN** 选中来源未被任何模块使用 / 前置图含环
- **WHEN** 计算覆盖报告
- **THEN** 前者 MUST 出现在缺失列表且覆盖比小于 1，后者 MUST 标记为有环

### Requirement: Single Question Slot

系统 SHALL 保证每个路径至多一个未终态题目。已有待答题时再次出题 MUST 重放同一张卡、忽略新参数，并 MUST 告知未产生新题。出题成功 MUST 结束本回合，其后工具 MUST NOT 执行。

#### Scenario: TC-K2 TC-E1 single slot

- **GIVEN** 已存在未作答的题目
- **WHEN** 再次注册题目
- **THEN** 系统 MUST 返回已存在状态且 MUST NOT 产生新题

#### Scenario: TC-E2 register after graded

- **GIVEN** 现有题目已进入已判分终态
- **WHEN** 注册新题
- **THEN** 系统 MUST 成功

### Requirement: Deterministic Grading

系统 SHALL 在注册时固定全部参照物，判分时刻 MUST NOT 调用 LLM。判分 MUST 按幂等键 `(path_id, interaction_id)` 定位事务：已判分时 MUST 返回首次结果且不新增证据；无法解析时 MUST 返回未判定且零状态变更，MUST NOT 判错。有提示作答 MUST 降级为部分正确。

#### Scenario: TC-K1 registration validation

- **GIVEN** 选择题选项体重复 / 期望答案无法解析为标签 / 简答题带选项
- **WHEN** 注册题目
- **THEN** 系统 MUST 拒绝

#### Scenario: TC-K3 TC-E3 idempotent grading

- **GIVEN** 同一 `question_id` 已判分
- **WHEN** 重复判分
- **THEN** 系统 MUST 返回首次结果且证据条数 MUST 不变

#### Scenario: TC-K4 TC-H2 ungraded zero change

- **GIVEN** 作答无法解析为标签且不唯一匹配
- **WHEN** 判分
- **THEN** 系统 MUST 返回未判定且 `mastery_levels` MUST 不变

#### Scenario: TC-H1 body unique match

- **GIVEN** 选择题作答写了选项正文而非标签，且正文唯一匹配
- **WHEN** 判分
- **THEN** 系统 MUST 判为正确

#### Scenario: TC-H3 TC-H4 short answer matching

- **GIVEN** 简答题
- **WHEN** 作答为 `2` 对 `2.0`、`2` 对 `3`、相似度 `0.88`、相似度 `0.95`
- **THEN** 结果 MUST 依次为正确、错误、错误、正确
- **AND** 超过 30 字的作答 MUST 只做精确匹配

#### Scenario: TC-H5 open answer is weakest

- **GIVEN** 开放题命中全部必需关键词但可选关键词命中率不足
- **WHEN** 判分
- **THEN** 结果 MUST 为部分正确且 MUST NOT 授予掌握

#### Scenario: TC-K5 TC-H6 hints downgrade

- **GIVEN** 作答时使用了提示
- **WHEN** 判分
- **THEN** 结果 MUST 降级为部分正确且门槛 MUST NOT 清除

### Requirement: Qualitative Assessment

系统 SHALL 只允许对 `concept` / `design` 执行定性评估，对 `memory` / `procedure` MUST 拒绝。定性通过 MUST 清除门槛，量化分 MUST NOT 打开这两类目标。

#### Scenario: TC-M3 assess scope

- **GIVEN** 知识点类型为 `concept` / `memory`
- **WHEN** 调用定性评估
- **THEN** 前者 MUST 记录定性通过并推进，后者 MUST 被拒绝

### Requirement: Capture Without Structure Change

系统 SHALL 允许 `study` / `review` 下的「提议候选目标」与「记录缺口」只写入候选与缺口记录，MUST NOT 增删改模块或知识点，MUST NOT 影响地图、下一目标与门槛。候选 MUST 仅在 `outline` 模式下经 `revise` 才成为知识点。

#### Scenario: TC-M1 TC-M4 capture isolation

- **GIVEN** `study` 模式
- **WHEN** 提议一个候选目标
- **THEN** 系统 MUST 写入候选并发出事件，地图与下一目标 MUST 不变

### Requirement: Repair Recomputes Instead Of Deleting

系统 SHALL 通过作废或改正题目来修复错误评估，MUST NOT 物理删除历史证据。作废后 MUST 重算掌握度与复习状态；改正后 MUST 重判已存作答并追加新证据。

#### Scenario: TC-M5 repair void

- **GIVEN** 一条错误判分证据
- **WHEN** 作废该题
- **THEN** 证据 MUST 被标记作废、门槛 MUST 重新计算，历史记录 MUST 仍可查

### Requirement: Gap Classification

系统 SHALL 区分目标缺口与来源遗漏，并 MUST NOT 静默重建或静默丢弃内容。接受缺口后该知识点 MUST 标记为未接地且可查询；补充来源后 MUST 自动消解。

#### Scenario: TC-F2 TC-F3 TC-F4 gap routing

- **GIVEN** 缺口为「来源里根本没有」/「来源遗漏」/「已接受」
- **WHEN** 选择重新生成 / 重新生成 / 补充来源
- **THEN** 缺口 MUST 仍存在 / 闭合 / 角标消失
