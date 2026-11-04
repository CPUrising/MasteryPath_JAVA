# Proposal: 题库读写

## Why

题库（M6）的两个入口在 `0008` 就已落地，但**语义与参考实现相反**，且当时的测试把这些
错误行为写成了断言。用参考实现跑真实代码确认了 5 处差异（见 `design.md` §Context 的对照表）：

| 场景 | 参考实现 | 当前 Java |
| --- | --- | --- |
| `tag_question(['b','c'])` | **整体替换** → `['b','c']` | 追加 → `['a','b','c']` |
| 空白标签 `['', '  ', 'real']` | **保留** → `['', '  ', 'real']` | 过滤掉 |
| 条目不存在 | **抛** `unknown question bank entry: x` | 返回 `null` |
| `tag_question([])` | 清空标签 | 无变化 |
| `list_questions(tag='  ')` | 空白是**真值**，过滤 → 0 条 | `isBlank()` → 返回全部 |

「整体替换」与「追加」是**语义级差异**：前者是「这一组就是全部标签」，后者是「在已有基础上加」。
前端如果按替换语义实现（编辑标签后提交全量），Java 侧会不断累积旧标签。

## What Changes

- `LearningService.tagQuestion` 改为**整体替换**语义：
  - 每个标签 `String.valueOf(...)`（`1` → `"1"`、`null` → `"None"`）；
  - 去重且保序（等价 `dict.fromkeys`）；
  - **不过滤**空串与空白（参考实现不校验）；
  - 条目不存在时抛 `PreconditionFailedException("unknown question bank entry: " + entryId)`。
- `LearningService.listQuestions` 的 `tag` 判定改为**真值语义**（`tag != null && !tag.isEmpty()`），
  不再用 `isBlank()`——空白字符串在参考实现里是真值，会参与过滤。

## Capabilities

### New Capabilities

无新增能力；补齐 `specs/learning-tools` 的题库读写侧（该规范未单列题库需求，
行为以参考实现 `service.py:990-1001` 为唯一依据）。

### Modified Capabilities

无。`skip_specs: true`：本 change 是把**已写错的实现改回参考实现的行为**，
不引入新的行为合同。

## Impact

- 影响文件：
  - `src/main/java/com/masterypath/service/LearningService.java`（`tagQuestion` / `listQuestions`）
  - `src/test/java/com/masterypath/service/QuestionBankTest.java`（3 处错误断言 + 4 个新用例）
- 影响测试：`QuestionBankTest` 的 `taggingDeduplicates` / `blankTagsIgnored` /
  `tagMissingEntryReturnsNull` 三个用例**断言的是错误行为，必须改写**。
- 明确不做（Out of scope）：
  - `GET /learning/paths/{id}/questions` 与 `POST /learning/questions/{id}/tags` 端点 → `0014`。
  - `repair_question` 里的题库同步（`void` / `correct`）→ `0012`。
  - 题库条目的「来源链接」字段（`INV-25`：未接地不带来源链接）→ 参考实现当前不写该字段，
    照抄；若将来补，另开 change。
