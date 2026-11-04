# Tasks

## 1. 语义修正

- [x] 1.1 `tagQuestion` 改为整体替换：`String.valueOf` 转换（`null` → `"None"`）、`LinkedHashSet` 去重保序、**不过滤空白**
- [x] 1.2 `tagQuestion` 条目不存在时抛 `PreconditionFailedException("unknown question bank entry: " + entryId)`
- [x] 1.3 `listQuestions` 的 `tag` 判定改为真值语义（`!= null && !isEmpty()`），空白字符串参与过滤

## 2. 测试

- [x] 2.1 改写 `taggingDeduplicates` → `taggingReplacesInsteadOfAppending`：断言**整体替换**（`['a','b']` 后 `['b','c']` → `['b','c']`）
- [x] 2.2 改写 `blankTagsIgnored` → `blankTagsPreserved`：断言空白**被保留**
- [x] 2.3 改写 `tagMissingEntryReturnsNull` → `tagMissingEntryThrows`：断言文案逐字
- [x] 2.4 新增：`tagQuestion([])` 清空标签
- [x] 2.5 新增：`listQuestions(tag="  ")` 返回 0 条（空白是真值）
- [x] 2.6 新增：`listQuestions(tag="")` 与 `null` 返回全部
- [x] 2.7 新增：非字符串标签经 `str()` 转换（`null` → `"None"`）；另补 `taggingDeduplicatesWithinBatch`

## 3. 验证

- [x] 3.1 `.\scripts\verify.ps1 -Scope unit -Change 0011-question-bank` → 345 通过
- [x] 3.2 全量 `.\scripts\verify.ps1 -Change 0011-question-bank` → 414 通过
- [x] 3.3 与参考实现对照（`pytest tests/test_question_bank.py -q`）→ 5 passed；另用真实 Python 探针做跨语言数据等价验证
- [x] 3.4 回填 `docs/verify/0011-question-bank.md`、`docs/regression-log.md`、`docs/traceability.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0011-question-bank.md`、`docs/migration-plan.md` §11（新增 Q5）
