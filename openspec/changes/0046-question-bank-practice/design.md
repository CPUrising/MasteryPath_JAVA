# Design: 题库刷题与标签管理

## 1. 可刷题判定

题库存在三种题型：

| 题型 | 来源 | 可刷 | 原因 |
| --- | --- | --- | --- |
| `choice` | `mastery_quiz` 注册后判分写入，含 `options` + `correct_answer` | 是 | 可确定性判分 |
| `short` | 同上，含 `correct_answer` | 是 | 可确定性判分 |
| `open` | 同上，但题库**不保存** `required_keywords` / `optional_keywords` | 否 | 无关键词，判分只能 `ungraded` |
| `written` | 定性评估写入，无标准答案 | 否 | 定性证据不可复用为题目 |

`practiceable(entry)` 的完整条件：

1. `question_type` 忽略大小写后是 `choice` 或 `short`；
2. `correct_answer` 非空白；
3. `choice`：`options` 非空，且 `correct_answer.trim().toUpperCase()` 命中某个选项 `label`。

不满足条件的题仍可在工作台查看与打标，只是不进入刷题队列。

## 2. 判分复用

`QuestionBankGrading` 把条目适配成 `PendingQuestion` 后直接调用 `Grading.grade`：

- 不复制 `Grading` 的归一化/模糊匹配/关键词逻辑；
- `ungraded` 永不记为错（INV-46）；
- 不调用 LLM（INV-32）。

## 3. 刷题不影响掌握

`POST /questions/{entryId}/attempt` 只调用 `LearningStore.mutateBankEntry` 回写
`user_answer` / `is_correct` / `result` 三个字段（`updated_at` 由 store 自动刷新）。

- 不调用 `LearningService.grade`；
- 不进入 path CAS（`store.mutate(pathId, fn)`）；
- 不 `tx.emit(...)`，不写 `events` / `interactions`；
- 不改 `mastery_levels` / `learning_evidence` / `pending_question` / `repetition_states`。

这与 `quiz-set` 的「练习不授予掌握」一致。

## 4. 标签管理为派生实现

不新增 `tags` 注册表。所有标签操作都扫描 `question_bank.data` 中的 `tags`：

- `rename` / `merge` / `delete` 工作区全量生效，逐条 `mutateBankEntry`；
- 去重保序：保留首次出现位置；
- 空白标签在**汇总视图**中被忽略（无法管理），但底层 `LearningService.tagQuestion`
  的「不过滤空白」语义原样保留（参考实现等价性）。

这样避免改 DDL，参考实现的 `learning.db` 可以继续直接打开。

## 5. 筛选语义

- 多标签 **AND**：条目必须包含全部选中标签。
- `result=wrong` 等价于 `result ∈ {incorrect, partial, voided}`。
- `q` 对题干与知识点名做忽略大小写的包含匹配。
- 省略 `path_id` 即跨全部路径聚合。

## 6. 答案隐藏

刷题队列使用 `QuestionBankProjector.practiceCard` 白名单投影，恰好 11 键：
`entry_id / path_id / path_name / knowledge_point_id / knowledge_point_name / question /
question_type / options / difficulty / tags / result`。

禁止出现 `correct_answer` / `explanation` / `user_answer` / `is_correct`（INV-07）。
答案只在作答端点返回之后释放，供复盘。

## 7. 错误映射

| 情况 | 异常 | HTTP |
| --- | --- | --- |
| 未知 `entryId` | `PreconditionFailedException("unknown question bank entry: {id}")` | 404 |
| 题目不可刷 | `IllegalArgumentException("question is not practiceable: {id}")` | 422 |
| 标签名为空白 | `IllegalArgumentException("tag name must not be blank")` | 422 |
| `entry_ids` 为空 | `IllegalArgumentException("entry_ids must not be empty")` | 422 |

## 8. 数量与排序

- 刷题 `size` 默认 10，夹取 1–50。
- `order`：`newest`（默认，创建时间降序）/ `wrong_first`（`is_correct=false` 在前，组内仍按创建时间降序）/ `random`（`Collections.shuffle`）。
- 搜索 `limit` 默认 100，夹取 1–500；`offset` 小于 0 按 0。
