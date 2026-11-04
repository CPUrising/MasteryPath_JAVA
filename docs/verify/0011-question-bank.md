# 验证报告：0011-question-bank

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0011-question-bank` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-04 15:23:30（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0011-question-bank` |
| 日志 | `artifacts/verify-0011-question-bank-20261104-152330.log` |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

本 change 修正 `0008` 遗留的**语义错误**（不是新增能力）。逐项对照参考实现
`service.py:990-1001` 的真实输出：

| # | 场景 | 参考实现输出 | 修正前 Java | 修正后 Java | 测试方法 | 结果 |
|---|---|---|---|---|---|---|
| 1 | `tag(['a','b'])` 后 `tag(['b','c'])` | `['b','c']` | `['a','b','c']` | `['b','c']` | `QuestionBankTest#taggingReplacesInsteadOfAppending` | PASS |
| 2 | 同批重复 `['exam','hard','exam']` | `['exam','hard']` | — | `['exam','hard']` | `#taggingDeduplicatesWithinBatch` | PASS |
| 3 | 空白 `['', '  ', 'real']` | `['', '  ', 'real']` | `['real']` | `['', '  ', 'real']` | `#blankTagsPreserved` | PASS |
| 4 | `tag([])` | `[]`（清空） | 无变化 | `[]` | `#emptyListClearsTags` | PASS |
| 5 | `tag(['ok', null])` | `['ok', 'None']` | — | `['ok', 'None']` | `#nullTagBecomesPythonNone` | PASS |
| 6 | `tag('nope', ['x'])` | 抛 `unknown question bank entry: nope` | 返回 `null` | 抛同文案 | `#tagMissingEntryThrows` | PASS |
| 7 | `list(tag='  ')` | `0` 条 | 全部 | `0` 条 | `#blankTagStillFilters` | PASS |
| 8 | `list(tag='')` / `tag=None` | 全部 | 全部 | 全部 | `#falsyTagReturnsAll` | PASS |

`0008` 原有、本 change 保留的用例（行为本已正确）：

| 场景 | 测试方法 | 结果 |
|---|---|---|
| 判分入库字段齐全 | `#bankEntryCapturesGrading` | PASS |
| 答错也入库 | `#wrongAnswerAlsoStored` | PASS |
| 无标签返回全部 | `#listAllWhenNoTag` | PASS |
| 按标签筛选 | `#filterByTag` | PASS |
| 题库按创建时间倒序 | `#bankOrderedByCreationDesc` | PASS |
| 空路径题库为空 | `#emptyBank` | PASS |

本 change 后 `QuestionBankTest` 共 **14 个用例**（原 9 个，其中 3 个被改写、5 个新增）。
全量 **414 个，0 失败**（409 → 414）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 纯函数专项 | `verify.ps1 -Scope unit` | Tests run: 345, Failures: 0 |
| 全量 | `verify.ps1 -Change 0011-question-bank` | BUILD SUCCESS，Tests run: 414, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（0010 的 409 项全部仍通过） |
| 参考实现对照 | `pytest tests/test_question_bank.py -q`（在 `../MasteryPath`） | **5 passed** |

### 跨语言数据等价实证

用真实 Python 代码跑同一组操作序列（临时探针，已删除），输出与 Java 断言逐条一致：

```
replace_1: ['a', 'b']
replace_2: ['b', 'c']          ← 整体替换
blanks:    ['', '  ', 'real']  ← 不过滤
cleared:   []                  ← 空列表清空
filter_blank: 0                ← '  ' 是真值
filter_empty: 1                ← '' 是假值
filter_none:  1                ← None 是假值
filter_exam:  1
missing: PreconditionError | unknown question bank entry: nope
```

### 与参考实现的对齐方式

| Python 用例 | Java 对应 | 说明 |
|---|---|---|
| `test_graded_question_is_synced_to_bank` | `#bankEntryCapturesGrading` | 同断言 |
| `test_qualitative_assessment_is_synced_to_bank` | `QualitativeAssessTest#writesBankEntry`（`0009`） | 已覆盖 |
| `test_tags_are_manual_and_deduped` | `#taggingDeduplicatesWithinBatch` | 同断言（该 Python 用例只测单批去重，不测替换） |
| `test_list_questions_filter_by_tag` | `#filterByTag` | 同断言 |
| `test_api_questions_and_tags` | 待 `0014`（REST 端点） | 服务层已就绪 |

### Java 侧超出参考测试覆盖的部分

| Java 用例 | 补齐的空白 |
|---|---|
| `#taggingReplacesInsteadOfAppending` | 参考实现**无用例**证明是替换而非追加（这正是 `0008` 写错的地方） |
| `#blankTagsPreserved` | 参考实现无「空白标签保留」用例（`0008` 写成了过滤） |
| `#emptyListClearsTags` | 参考实现无清空用例 |
| `#tagMissingEntryThrows` | 参考实现有抛错实现但**无用例** |
| `#blankTagStillFilters` / `#falsyTagReturnsAll` | 参考实现无 `tag` 真值语义用例 |
| `#nullTagBecomesPythonNone` | 参考实现无 `str(None)` 用例 |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `0008` 把 `tag_question` 实现成**追加**而非整体替换 | **重要**（已修复） | 语义级错误：前端按替换提交会导致旧标签永久累积。已改回参考实现语义并改写断言 |
| D2 | `0008` 把空白标签**过滤掉** | **重要**（已修复） | 违反「行为等价重写」：参考实现不校验。后果是跨语言数据不等价 |
| D3 | `0008` 对不存在条目返回 `null` 而非抛错 | 一般（已修复） | 调用方会误以为「成功但无数据」 |
| D4 | `0008` 用 `isBlank()` 判断 `tag`，与 Python 真值语义不符 | 一般（已修复） | `'  '` 在 Python 是真值 |
| D5 | 三个 `0008` 用例**断言了错误行为** | **重要**（已改写） | `taggingDeduplicates` / `blankTagsIgnored` / `tagMissingEntryReturnsNull` 已重写为正确语义 |
| D6 | 未新建 `service/QuestionBankService` | 一般 | 参考实现两个方法共 12 行，就在 `LearningService`；与 `0009`/`0010` 同理由 |
| D7 | 未实现 REST 端点 | 无 | 属 `0014` |

**D1–D5 的性质说明**：这不是「实现细节差异」，而是 `0008` 阶段把**与参考实现相反的行为**
连测试一起固化了下来。这类错误只有「跑参考实现真实代码 + 逐条对照」才能发现，
读代码很容易把 `entry.tags.add(...)` 看成合理实现。

## 5. 未覆盖项（已登记验证债）

| 场景 | 归属 change | 原因 |
|---|---|---|
| `GET /learning/paths/{id}/questions`、`POST /learning/questions/{id}/tags` | `0014` | 需 REST 层 |
| `repair_question` 的题库同步（`void` / `correct`） | `0012` | 需题目修复 |
| 题库条目「来源链接」（`INV-25`） | — | 参考实现当前不写该字段，照抄 |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 414, Failures: 0` 来自
`artifacts/verify-0011-question-bank-20261104-152330.log`；参考实现对照见
`pytest tests/test_question_bank.py` 的 `5 passed`；跨语言数据等价由真实 Python 探针
逐条验证（输出见 §3）。
