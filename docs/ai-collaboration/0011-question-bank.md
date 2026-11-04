# AI 协作留痕：0011-question-bank

## 1. prompt 范围

- 授权修改的文件：`src/main/java/com/masterypath/service/LearningService.java`（`tagQuestion` / `listQuestions`）、
  `src/test/java/com/masterypath/service/QuestionBankTest.java`、
  `openspec/changes/0011-question-bank/**`、`docs/**`。
- 明确不做的事：不实现 REST 端点（`0014`）、不做 `repair_question` 的题库同步（`0012`）、
  不给题库加「来源链接」字段。
- 出处：用户指令原文「继续计划」；`docs/migration-plan.md` §5 第 11 行。

## 2. AI 产出

- 修改文件：`LearningService.tagQuestion`（整体替换 + 抛错 + `str()` 转换）、
  `LearningService.listQuestions`（真值语义）、新增私有 `pythonStrings`
- 修改测试：`QuestionBankTest` —— 改写 3 个**断言了错误行为**的用例，新增 5 个
- 关键决策：
  - **`tagQuestion` 是整体替换，不是追加**：参考实现 `entry.tags = list(dict.fromkeys(...))`
    右侧是全新列表，赋值覆盖。语义是「调用方提交完整标签集」。
  - **不过滤空串与空白**：参考实现无任何校验。`0008` 加了 `!tag.isBlank()` 属「顺手加固」，
    违反「行为等价重写」，且导致跨语言数据不等价。
  - **`null` → `"None"`**：Python `str(None) == 'None'`；Java `String.valueOf(null)` 会得 `"null"`，
    必须显式映射。
  - **条目不存在抛错**：参考实现 `raise PreconditionError(f"unknown question bank entry: {entry_id}")`；
    `0008` 返回 `null`，让调用方误判为「成功但无数据」。
  - **`tag` 用真值语义**：`'  '` 在 Python 是真值（会过滤），`''`/`None` 是假值（不过滤）。
    `0008` 用 `isBlank()` 把 `'  '` 也当空值。

## 3. 人工修正点

- 无人类介入（人类尚未审查本 change 产出）。
- **本 change 的实质是修正 `0008` 的错误**，且是 AI 自己在 `0008` 写错的。
  发现路径：读参考实现代码时注意到 `entry.tags = list(...)` 是赋值而非 `+=`，
  但没有直接改——先用**真实 Python 代码跑探针**（`artifacts/probe_0011*.py`，已删除）实测确认，
  再动手。这一步是必要的：`0008` 的三条错误断言看起来都「合理」。

## 4. 回归结果

- 用例数 / 失败数：`QuestionBankTest` 14 / 0（原 9，改写 3 + 新增 5）；全量 414 / 0（409 → 414）。
- 证据链接：[`docs/verify/0011-question-bank.md`](../verify/0011-question-bank.md)
- 日志：`artifacts/verify-0011-question-bank-20261104-152330.log`
- 参考实现对照：在 `../MasteryPath` 执行 `pytest tests/test_question_bank.py -q` → **5 passed**。
- 跨语言数据等价：真实 Python 探针输出与 Java 断言逐条一致（见 verify 报告 §3）。

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| `tagQuestion` 是替换还是追加 | **整体替换** | 参考实现 `entry.tags = list(dict.fromkeys(...))` 是赋值；实测 `['a','b']` 后 `['b','c']` 得 `['b','c']` |
| 空白标签是否过滤 | **不过滤** | 参考实现无校验；实测 `['', '  ', 'real']` 原样保留 |
| `null` 转成什么 | **`"None"`** | Python `str(None)`；`String.valueOf` 会得 `"null"`，不一致 |
| 条目不存在时 | **抛 `PreconditionFailedException`** | 参考实现 `raise PreconditionError`；文案逐字 |
| `tag='  '` 是否过滤 | **过滤（返回 0 条）** | Python `if tag:` 对 `'  '` 为真 |
| 是否新建 `QuestionBankService` | **不新建** | 参考实现两个方法共 12 行，就在 `LearningService` |
| `0008` 的三个用例怎么处理 | **改写，不保留** | 它们断言的是与参考实现相反的行为；保留即锁定错误 |
| 是否把 Q5 登记为参考实现瑕疵 | **登记** | 保留空白标签看起来像脏数据，但参考实现如此；照抄并锁定 |
