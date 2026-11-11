# question-bank Specification (delta)

## ADDED Requirements

### Requirement: Derive Knowledge Base Membership

题库条目 SHALL 按其所属学习路径绑定的知识库派生归属关系。
一条路径绑定多个知识库时，该路径下的每道题 MUST 同时属于这些知识库。
路径未绑定任何知识库或路径已不存在时，题目 MUST 归入「未关联」。

归属 MUST 由 `paths.data → sources[]` 中 `kind == "knowledge_base"` 的条目解析：
`sourceId` 为空时回退 `id` 作为知识库 id，`label` 为空时回退 id 作为名称。
派生归属 MUST NOT 写入 `question_bank.data`，MUST NOT 新增表或列。

#### Scenario: Single knowledge base

- **GIVEN** 一条绑定知识库 `kb1` 的路径，其题库有 2 道题
- **WHEN** 读取题目归属
- **THEN** 两道题的 `knowledge_bases` MUST 均为 `[{id: "kb1", name: "…"}]`

#### Scenario: Multiple knowledge bases on one path

- **GIVEN** 一条同时绑定 `kb1` 与 `kb2` 的路径
- **WHEN** 读取该路径任一题目的归属
- **THEN** `knowledge_bases` MUST 同时包含 `kb1` 与 `kb2`

#### Scenario: Unassigned questions

- **GIVEN** 一条没有绑定任何知识库的路径
- **WHEN** 读取其题目的归属
- **THEN** `knowledge_bases` MUST 为空列表，并在分组中计入「未关联」

#### Scenario: Data stays byte-compatible

- **GIVEN** 任意题库条目
- **WHEN** 经服务层读取并回填归属后再写回
- **THEN** `question_bank.data` MUST NOT 出现 `knowledge_bases` 字段

### Requirement: Knowledge Base Grouping And Filtering

系统 SHALL 提供按知识库聚合的题库分组查询，返回 `knowledge_base_id`、`name` 与 `total`；
未关联题目 MUST 作为独立分组返回，且排在最后。
题库检索与刷题队列 SHALL 支持 `knowledge_base_ids` 过滤，语义 MUST 为 **OR**（属于任一即命中）；
未提供该参数 MUST NOT 过滤。

`GET /questions`（参考实现契约端点）的输出 MUST 保持既有字段集合不变。

#### Scenario: Groups count multi-membership

- **GIVEN** 一条同时绑定 `kb1`、`kb2` 的路径有 1 道题，另一条只绑定 `kb1` 的路径有 1 道题
- **WHEN** 读取分组
- **THEN** `kb1` 的 `total` MUST 为 2，`kb2` 的 `total` MUST 为 1

#### Scenario: OR filter

- **GIVEN** 题库同时存在属于 `kb1`、`kb2` 的题目
- **WHEN** 以 `knowledge_base_ids=kb1,kb2` 检索
- **THEN** 返回属于任一知识库的题目

#### Scenario: Unknown knowledge base id filters everything out

- **GIVEN** 不存在的知识库 id
- **WHEN** 以其过滤
- **THEN** 返回空列表，且 MUST NOT 返回错误

#### Scenario: Practice card hides answers

- **GIVEN** 一道可刷题目
- **WHEN** 读取刷题队列
- **THEN** 卡片 MUST 含 `knowledge_bases`，且 MUST NOT 含
  `correct_answer` / `explanation` / `user_answer` / `is_correct`
