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

系统 SHALL 在注册时固定参照物，判分时刻 MUST NOT 调用 LLM。判分 MUST 按幂等键
`(path_id, interaction_id)` 定位。结果 SHALL 为 `correct` / `incorrect` / `partial` / `ungraded`；
无法解析的作答 MUST 返回 `ungraded` 且零状态变更（不写 attempt/证据、不改掌握度、不清待答题）。
开放题判对 MUST 在服务层降为 `partial`（`INV-35`）。有提示的作答 MUST 记录
`independent=false`，结果 MUST NOT 被降级。各题型的匹配细节见场景 `Per-type matching rules`。

#### Scenario: Per-type matching rules

- **GIVEN** 一道 `choice` / `short` / `open` 题
- **WHEN** 判分
- **THEN** `choice` MUST 先按标签命中，否则按选项正文归一化后唯一匹配
- **AND** `short` MUST 依次尝试精确、`accepted_answers`、数值容差 1e-6、相似度 ≥0.90，超过 30 字只做精确匹配
- **AND** `open` MUST 使用注册时固定的 `required_keywords` / `optional_keywords`，required 全中且 optional 命中率 ≥0.6 判对
- **AND** 空作答与未注册关键词 MUST 返回 `ungraded`

#### Scenario: TC-K1 registration validation

- **GIVEN** 选择题选项体重复 / 期望答案无法解析为标签 / 简答题带选项
- **WHEN** 注册题目
- **THEN** 系统 MUST 拒绝

#### Scenario: TC-K3 TC-E3 idempotent grading

- **GIVEN** 同一 `question_id` 已判分
- **WHEN** 重复判分
- **THEN** 系统 MUST 返回首次结果且证据条数 MUST 不变

#### Scenario: TC-K4 TC-H2 ungraded zero change

- **GIVEN** 选择题期望答案为 `B`
- **WHEN** 作答为 `zzz`（无法解析为标签）
- **THEN** 结果 MUST 为 `ungraded`
- **AND** 系统 MUST NOT 写入 attempt / 证据，掌握度与待答题 MUST 保持不变

#### Scenario: TC-H1 body unique match

- **GIVEN** 选择题期望答案为标签 `B`，且 `B` 的正文唯一
- **WHEN** 作答为选项正文 / ` b ` / `B`
- **THEN** 三种作答 MUST 均判 `correct`

#### Scenario: TC-H3 TC-H4 short answer matching

- **GIVEN** 简答题
- **WHEN** 作答与期望答案完全一致 / 相似度低于 0.90 / 期望答案超过 30 字且不完全一致
- **THEN** 结果 MUST 依次为正确、错误、错误
- **AND** 期望答案 ≤30 字且相似度 ≥0.90 时 MUST 判对
- **AND** 空作答 MUST 返回 `ungraded`

#### Scenario: TC-H5 open answer is weakest

- **GIVEN** 开放题注册了 required 与 optional 关键词
- **WHEN** required 全中且 optional 命中率达到 0.6 / 低于 0.6 / required 未全中
- **THEN** 结果 MUST 依次为 `correct`、`partial`、`incorrect` 或 `partial`
- **AND** 服务层 MUST 把开放题的 `correct` 降为 `partial`

#### Scenario: TC-K5 TC-H6 hints downgrade

- **GIVEN** 作答时使用了提示
- **WHEN** 判分
- **THEN** 结果 MUST 仍为 `correct`，但作答记录 MUST 标记 `independent=false`
- **AND** 结果 MUST NOT 被降级为 `partial`

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

系统 SHALL 通过作废或改正题目来修复错误评估，MUST NOT 物理删除历史证据。作废后 MUST 重算掌握度与复习状态；改正后 MUST 重判已存作答并追加新证据。改正后重判若为 `ungraded`，系统 MUST 拒绝该次修复并返回可读原因，MUST NOT 写入「已纠正」。返回体 MUST 含 `is_correct` 与 `result`。

#### Scenario: TC-M5 repair void

- **GIVEN** 一条错误判分证据
- **WHEN** 作废该题
- **THEN** 证据 MUST 被标记作废、门槛 MUST 重新计算，历史记录 MUST 仍可查

#### Scenario: Corrected key still ungradable is refused

- **GIVEN** 题目已判分，且新答案键与已存作答仍无法判定
- **WHEN** 以 `correct` 修复
- **THEN** 系统 MUST 抛前置条件错误，且 MUST NOT 写 `assessment.corrected`
- **AND** `mastery_repair_question` MUST NOT 回「已纠正」

#### Scenario: Corrected key is reported truthfully

- **GIVEN** 新答案键使已存作答可以判定
- **WHEN** 以 `correct` 修复
- **THEN** 返回体 MUST 含真实的 `is_correct` 与 `result`
- **AND** 工具回执 MUST 带出该 `result`

### Requirement: Gap Classification

系统 SHALL 区分目标缺口与来源遗漏，并 MUST NOT 静默重建或静默丢弃内容。接受缺口后该知识点 MUST 标记为未接地且可查询；补充来源后 MUST 自动消解。

#### Scenario: TC-F2 TC-F3 TC-F4 gap routing

- **GIVEN** 缺口为「来源里根本没有」/「来源遗漏」/「已接受」
- **WHEN** 选择重新生成 / 重新生成 / 补充来源
- **THEN** 缺口 MUST 仍存在 / 闭合 / 角标消失

### Requirement: Grade Feedback Persistence

`POST /learning/paths/{id}/grade` SHALL 接受可选 `session_id` 与 `turn_id`。只有真正 `graded`
的结果 MUST 以 user 答案 + assistant `data.grade` 写入会话消息；`ungraded` MUST 返回 422、
MUST NOT 写入会话消息、MUST NOT 改变掌握度或证据。同一 `question_id` 的重复判分 MUST NOT 重复写入消息。

#### Scenario: Graded feedback persists

- **GIVEN** 一次带 `session_id` 的题卡作答且判分成功
- **WHEN** 重新读取会话详情
- **THEN** 消息 MUST 同时包含用户答案与 assistant 的 `data.grade`，且含 `result` 与 `is_correct`

#### Scenario: Ungraded persists with zero change

- **GIVEN** 一次无法解析的作答
- **WHEN** 判分返回 `ungraded`
- **THEN** 响应 MUST 为 422，会话 MUST NOT 新增消息
- **AND** 掌握度、证据与待答题 MUST 不变

#### Scenario: Repeated grade does not duplicate messages

- **GIVEN** 同一 `question_id` 已判分并已落库
- **WHEN** 再次提交同一作答
- **THEN** 系统 MUST 返回首次结果，且会话消息 MUST NOT 新增重复记录

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

### Requirement: Status Stage And Step Metadata

`mastery_status` 与 REST 路径状态 SHALL 为每个知识点返回 `learning_stages`：
`not_yet_covered` | `explained` | `practiced_not_demonstrated` | `practiced_with_help` |
`independently_demonstrated`。`next` SHALL 带 `gate`（`qualitative` / `quantitative`）与
`forgetting_risk`。

#### Scenario: Learning stages cover every objective

- **GIVEN** 一条已建大纲的路径
- **WHEN** 读取 `mastery_status`
- **THEN** `learning_stages` MUST 以知识点 id 为键，覆盖全部目标
- **AND** 未触碰目标 MUST 为 `not_yet_covered`

#### Scenario: Next step reports its gate

- **GIVEN** 一个待推进的定量目标
- **WHEN** 读取 `next`
- **THEN** `gate` MUST 为 `quantitative`
- **AND** 定性目标的 `gate` MUST 为 `qualitative`

#### Scenario: Review step reports forgetting risk

- **GIVEN** 一个到期复习项带有非零遗忘风险
- **WHEN** `next.action` 为 `review`
- **THEN** `forgetting_risk` MUST 等于该复习项的风险值

### Requirement: Status Last Answered Evidence

`mastery_status` 与 REST 路径状态 SHALL 返回 `last_answered`，回放最近一次未作废的作答证据：
`question_id`、`knowledge_point_id`、`answer`、`result`、`assessment_type`、`hints_used`、
`mastery_after`、`session_id`、`turn_id`。从未作答时 MUST 为 `{status: "none"}`。
该投影 MUST NOT 含 `expected_answer` 或 `explanation`。

#### Scenario: No attempt yields none

- **GIVEN** 一条从未产生作答的路径
- **WHEN** 读取 `mastery_status`
- **THEN** `last_answered` MUST 为 `{status: "none"}`

#### Scenario: Latest attempt is replayed

- **GIVEN** 学习者在会话 `s1` 的回合 `t1` 中答对题 `q1`
- **WHEN** 读取 `mastery_status`
- **THEN** `last_answered` MUST 含 `question_id=q1`、`answer`、`result=correct`
- **AND** MUST 含 `session_id=s1`、`turn_id=t1` 与判分后的 `mastery_after`

### Requirement: Short Answer Length Contract

系统 SHALL 在注册 `short` 题时校验答案长度：归一化后的 `expected_answer` 超过 30 字时，
MUST 至少提供一条归一化后不超过 30 字的 `accepted_answers`，否则 MUST 拒绝注册并提示改用
`open`（`required_keywords` / `optional_keywords`）或补充短等价答案。`choice` 与 `open` MUST NOT
受此校验影响。

#### Scenario: Long short without a short equivalent is refused

- **GIVEN** 一道 `short` 题，期望答案是超过 30 字的自然语言解释，且未提供 `accepted_answers`
- **WHEN** 注册题目
- **THEN** 系统 MUST 拒绝，并提示改用 `question_type=open` 或补充不超过 30 字的等价答案

#### Scenario: Long short with a short equivalent is accepted

- **GIVEN** 一道 `short` 题，期望答案超过 30 字，但 `accepted_answers` 含一条不超过 30 字的等价值
- **WHEN** 注册题目
- **THEN** 系统 MUST 允许注册

#### Scenario: Open accepts long expected answers

- **GIVEN** 一道 `open` 题，`expected_answer` 超过 30 字
- **WHEN** 注册题目
- **THEN** 系统 MUST 允许注册
