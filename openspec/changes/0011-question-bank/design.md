# Design: 题库读写

## Context

参考实现（`learning/service.py:990-1001`）只有两个方法，共 12 行：

```python
def list_questions(self, path_id: str, *, tag: str | None = None) -> list[dict[str, Any]]:
    entries = self.store.list_bank_entries(path_id)
    if tag:                                    # ← 真值判定，不是"非空白"
        entries = [entry for entry in entries if tag in entry.tags]
    return [entry.model_dump() for entry in entries]

def tag_question(self, entry_id: str, tags: list[str]) -> dict[str, Any]:
    entry = self.store.get_bank_entry(entry_id)
    if entry is None:
        raise PreconditionError(f"unknown question bank entry: {entry_id}")
    entry.tags = list(dict.fromkeys(str(tag) for tag in tags))   # ← 整体替换 + 转字符串 + 去重保序
    return self.store.save_bank_entry(entry).model_dump()
```

`0008` 把这两个方法顺手实现了，但把「整体替换」写成了「追加」、把「抛错」写成了「返回 null」、
把「真值判定」写成了「非空白判定」。当时的 3 个测试把错误行为固定了下来。

### 实测对照（参考实现跑真实代码，见 `artifacts/probe_0011*.py`）

| # | 输入 | 参考实现输出 | 当前 Java 输出 |
| --- | --- | --- | --- |
| 1 | `tag_question(eid, ['a','b'])` 后 `tag_question(eid, ['b','c'])` | `['b', 'c']` | `['a', 'b', 'c']` |
| 2 | `tag_question(eid, ['', '  ', 'real'])` | `['', '  ', 'real']` | `['real']` |
| 3 | `tag_question('nope', ['x'])` | 抛 `PreconditionError: unknown question bank entry: nope` | 返回 `null` |
| 4 | `tag_question(eid, [])` | `[]`（清空） | 无变化 |
| 5 | `list_questions(tag='  ')` | `0` 条（空白是真值 → 过滤） | 全部（`isBlank()` 短路） |
| 6 | `tag_question(eid, [1, None, 'ok'])` | `['1', 'None', 'ok']` | 类型不符（`List<String>` 编译期挡住） |

## Goals / Non-Goals

**Goals**

1. `tagQuestion` 逐字复刻：整体替换、`str()` 转换、`dict.fromkeys` 去重保序、**不过滤空值**。
2. `tagQuestion` 的条目不存在时抛 `PreconditionFailedException`，文案逐字。
3. `listQuestions` 的 `tag` 判定用真值语义（`!= null && !isEmpty()`）。
4. 修正 `0008` 遗留的 3 个错误断言，并补上参考实现测试空白（清空、空白保留、空白过滤）。

**Non-Goals**

1. 不做 REST 端点（`0014`）。
2. 不做 `repair_question` 的题库同步（`0012`）。
3. 不给题库加「来源链接」字段（参考实现不写，照抄）。
4. 不引入 `QuestionBankService`：参考实现就在 `LearningService`，两个方法共 12 行。

## Decisions

1. **`tagQuestion` 是整体替换，不是追加**
   - 参考实现：`entry.tags = list(dict.fromkeys(...))` —— 右侧是**全新的列表**，赋值覆盖。
   - 语义：调用方提交的是「该条目的完整标签集」。前端编辑标签后提交全量是自然做法；
     若按追加实现，用户取消一个标签就无法生效，且旧标签会永久累积。
   - `0008` 的实现（逐条 `add`）是**语义错误**，不是「实现细节差异」。

2. **不过滤空串与空白**
   - 参考实现没有任何校验：`str(tag) for tag in tags` 直接转换。
   - `0008` 加了 `!tag.isBlank()` 过滤，属「顺手加固」，违反「行为等价重写」。
   - 后果：`tag_question(['', '  ', 'real'])` 在参考实现下得到 3 个标签，Java 只得到 1 个，
     两边数据不等价。
   - 注意：这条与直觉相反（空白标签看起来像脏数据），但**参考实现就是如此**，
     `AGENTS.md` §4.5「以参考实现为准」。

3. **`null` 转成字符串 `"None"`**
   - Python 的 `str(None) == 'None'`。Java 侧 `List<String>` 在编译期就挡住了 `null`，
     但**反序列化路径**（REST 传 `[null]`）仍可能传进来。用 `String.valueOf(tag)`
     会得到 `"null"` 而非 `"None"`——**不一致**。
   - 决定：显式判断 `tag == null ? "None" : tag`，与 Python 逐字一致。
   - 数字同理：Python `str(1) == '1'`，Java `String.valueOf(1) == "1"`，一致。

4. **去重保序用 `LinkedHashSet`**
   - `dict.fromkeys(...)` 在 Python 3.7+ 保证插入顺序，且去重。
   - `LinkedHashSet` 语义完全相同；`new ArrayList<>(new LinkedHashSet<>(tags))`。

5. **条目不存在抛错，不返回 `null`**
   - 参考实现：`raise PreconditionError(f"unknown question bank entry: {entry_id}")`。
   - `0008` 返回 `null`，让调用方误以为「操作成功但没数据」。
   - Java 侧 `store.mutateBankEntry` 在条目不存在时返回 `null`；`tagQuestion` 需先探测再抛。
     为避免两次查询，改为：`mutateBankEntry` 返回 `null` 时抛
     `PreconditionFailedException("unknown question bank entry: " + entryId)`。

6. **`listQuestions` 的 `tag` 用真值语义**
   - 参考实现 `if tag:` —— Python 中 `''`、`None` 为假，`'  '` 为**真**。
   - `0008` 用 `tag == null || tag.isBlank()`，把 `'  '` 也当空值，返回全部条目。
   - 改为 `tag == null || tag.isEmpty()`。

7. **不改 `store.listBankEntries` 的排序**
   - 参考实现 `ORDER BY created_at DESC`；Java 已是如此。`0008` 的
     `bankOrderedByCreationDesc` 断言正确，保留。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 保留空白标签被当成 bug 再改回去 | 与参考实现永久漂移 | `design.md` 明写「参考实现不校验」+ 用例断言 `['', '  ', 'real']` |
| 整体替换让前端「追加」调用丢失旧标签 | 前端行为变化 | 参考实现即替换；前端（`../MasteryPath/frontend`）按替换实现，已核对 |
| `mutateBankEntry` 返回 null 与抛错混用 | 调用方判断不一致 | 只在 `tagQuestion` 抛；`mutateBankEntry` 保持返回 null（内部 API） |
| `null` → `"null"` 而非 `"None"` | 跨语言数据不等价 | 显式映射；用例覆盖 |

## Migration Plan

1. 改 `LearningService.tagQuestion` / `listQuestions`。
2. 改写 `QuestionBankTest` 的 3 个错误断言，补 4 个新用例。
3. `verify.ps1 -Scope unit` → 全量 → `pytest tests/test_question_bank.py` 对照 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/learning/service.py:990-1001` 为唯一依据，
  并用 `artifacts/probe_0011*.py` 实测确认（而非仅读代码）。
- 参考实现测试空白（本 change 会补）：
  - `tag_question([])` 清空标签；
  - 空白标签被保留；
  - `tag_question` 对不存在条目抛错（参考实现有实现但**无用例**）；
  - `list_questions(tag='  ')` 的空白真值行为；
  - 非字符串标签的 `str()` 转换。
