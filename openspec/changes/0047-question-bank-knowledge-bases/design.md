# Design: 题库按知识库分类

## 1. 归属来源

路径绑定的知识库不是独立列，而是 `paths.data → sources[]` 中 `kind == "knowledge_base"` 的
`TopicSource`：

- `sourceId` 非空 → 知识库 id；为空时回退 `source.id`
- `label` → 知识库名称；为空时回退 id

解析逻辑与 `LearningService.getState` 的 `knowledge_bases` 保持一致（[LearningService.java:1031-1040](../../src/main/java/com/masterypath/service/LearningService.java)）。

## 2. 多知识库：多对多

一条路径可绑定多个知识库，因此一道题**同时属于**这些知识库。

- 不做「主知识库」单选：题库条目本身没有 KB 字段，选主库只能靠猜测。
- 路径未绑定任何知识库、或路径已被删除 → 归「未关联」（`knowledge_base_id = ""`）。
- 多归属导致 `/questions/kb-groups` 各组 `total` 之和 ≥ 总题数，这是预期语义，不是重复计数错误。

## 3. 筛选语义：OR

`knowledge_base_ids=A,B` → 属于 A 或 B 的题。理由：多归属下 AND 几乎必然为空，且学习者心智是
「看看这两个库出来的题」。

## 4. 不落库

派生字段用 `@JsonIgnore` 标注，Jackson 既不写也不读，`question_bank.data` 字节不变：

- 保持与参考实现的数据等价（`ReferenceWorkspaceCompatibilityTest` 全绿）
- 旧条目无需迁移即可获得归属
- 路径解绑知识库后，历史题目的归属自动回到「未关联」，不需要回填脚本

## 5. 性能

`QuestionBankService` 在单次请求内用 `store.listPaths()` 建 `pathId → 聚合` 索引，再逐条 `enrich`。
题库规模为百级，一次全表读可接受；索引不跨请求持有。

## 6. 契约兼容

`GET /questions` 是参考实现端点，输出必须是既有 17 键，**不追加** `knowledge_bases`。
知识库信息只出现在 `GET /questions/search`、`GET /questions/practice`、`GET /questions/kb-groups`。

## 7. 错误映射

`knowledge_base_ids` 无需校验：不存在的 id 不会命中任何条目，返回空列表（不报错）。
这与 `tags` 筛选的宽松语义一致。
