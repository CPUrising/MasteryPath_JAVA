# AI 协作留痕：0007-outline-build-revise

## 1. prompt 范围

- 授权修改的文件：`src/main/java/com/masterypath/domain/outline/**`（新增 7 个文件）、
  `src/main/java/com/masterypath/service/LearningService.java`（补 4 个方法）、
  `src/test/java/com/masterypath/domain/outline/**`（3 个测试类）、
  `src/test/java/com/masterypath/service/GapClassificationTest.java`、
  `scripts/verify.ps1`（补测试类过滤）、`openspec/changes/0007-outline-build-revise/**`、`docs/**`。
- 明确不做的事：不实现 `eval_outline`（`0018`）、`propose_objective`/`note_gap`（`0009`）、
  扫描文件夹（`0016`）、`reset` 独立入口（`0012`）。
- 出处：用户指令原文「需要 `git 提交` ；下一步是 `0007`」；`docs/migration-plan.md` §5 第 7 行。

## 2. AI 产出

- 新增文件：
  - `OutlineBuilder`（build / revise / computeCoverage / detectGaps / resolveGap /
    applyProposal / rejectProposal）
  - `SourceResolver`（`{label: id}` + 唯一基名别名）
  - `BuildResult` / `ReviseResult`（record）、`OutlineException`
  - 测试 4 个：`OutlineBuildTest`（22）、`OutlineReviseTest`（20）、
    `CoverageReportTest`（12）、`GapClassificationTest`（15）
- 修改文件：`LearningService`（补 `build` / `revise` / `resolveGap` / `getCoverage`）；
  `scripts/verify.ps1`（`-Scope unit` 补 `*OutlineBuildTest,*OutlineReviseTest`）
- 关键决策：
  - **`build` 必须先解析 payload 再判断 `confirm`**：顺序反了会让非法 payload 返回
    `refused_rebuild` 而不是抛错。有专门用例 `invalidPayloadThrowsBeforeConfirmCheck` 锁定。
  - **前置解析用两遍扫描**：第一遍分配 id 建 `name → id`，第二遍解析前置，
    因此支持**前向引用**（前置指向后面的知识点）。
  - **`revise` 的缺口 id 从 `g1` 重算，`build` 续号**：照抄参考实现的不对称。
  - **`resetLearningState` 会清空 `sourceGaps`**：所以 `confirm` 重建后缺口从 `g1` 开始，
    这是参考实现原样行为。
  - **`dropKpState` 逐项清 7 个 map + 3 个 list**：漏清任一项都会让已删除的知识点
    继续影响掌握判定或复习队列。
  - **`reported=false` 时 `missing` 必须为空**：否则会把全部来源都列为缺失（`TC-N3`）。
  - **空路径接地比为 1.0 而不是 0**：避免空路径被误判为不合格。

## 3. 人工修正点

- 无人类介入（人类尚未审查本 change 产出）。
- AI 自我修正（提交前完成）：
  1. 首次用 `Set-Content` 改测试文件写入 UTF-8 BOM，javac 报 `非法字符: '\ufeff'` →
     改用 `[System.IO.File]::WriteAllText` + `UTF8Encoding(false)` 去 BOM。
     后续不再用 shell 改源码文件。
  2. 初版 `gapIdsContinueFromExisting` 预期 `g2` 但实际 `g1` →
     **这是测试写错，不是实现错**：`confirm` 重建先 `resetLearningState` 清空 `sourceGaps`，
     续号起点变 0。改为两个用例：`gapIdsContinueFromExisting`（无 KP 时续号）与
     `rebuildResetsGapIds`（重建时重置）。
  3. 测试里误用不存在的 `getKnowledgePointsOfFirstModule()` → 改为
     `getModules().get(0).getKnowledgePoints()`。

## 4. 回归结果

- 用例数 / 失败数：本 change 新增 69 / 0；全量 262 / 0。
- 证据链接：[`docs/verify/0007-outline-build-revise.md`](../verify/0007-outline-build-revise.md)
- 日志：`artifacts/verify-0007-outline-build-revise-20261104-141059.log`
- 参考实现对照：在 `../MasteryPath` 执行 `pytest tests/test_outline.py tests/test_eval.py -q`
  → **18 passed**，与 Java 侧 14 个语义对应用例逐条对齐。

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| `build` 的解析与 confirm 判断顺序 | **先解析** | 否则非法 payload 返回 `refused_rebuild` 而非抛错，行为变化 |
| 前置解析扫描次数 | **两遍** | 支持前向引用；一遍扫描会让 `TC-N4` 的环形前置用例失败 |
| `revise` 缺口 id 是否续号 | **从 g1 重算** | 参考实现 `revise` 传 `existing_gap_count=0` |
| `build` 缺口 id 是否续号 | **续号** | 参考实现传 `len(progress.source_gaps)`；但 `confirm` 重建会先清空，故实际从 g1 |
| `dropKpState` 清理范围 | **7 map + 3 list** | 漏清任一项都会让已删除 KP 继续影响掌握/复习 |
| `reported=false` 时是否列 missing | **不列** | `TC-N3`；否则会把全部来源当缺失 |
| 空路径接地比 | **1.0** | 避免空路径被误判不合格 |
| `materials` 是否覆盖已有来源 | **不覆盖** | 只补给未声明来源的 KP（参考实现 `if not kp.source_ids`） |
| 来源标签未命中如何处理 | **丢弃** | 参考实现过滤 `if resolved` |
| 类型解析是否支持中文历史值 | **支持** | `KnowledgeType.fromValue` 是 `0002` 的 `INV-16` 行为，属合法超集 |
| `revise` 出错是否自行回滚 | **不回滚** | 外层 `store.mutate` 有事务；本层加 try/catch 会掩盖错误 |
| `applyProposal` 是否校验模式 | **不校验** | 模式门控在工具层（`0005` + `0007` 的 `MasteryTools`），纯函数层不管 |

## 6. 已知参考实现行为瑕疵（本 change 新增登记）

| # | 现象 | 处置 |
| --- | --- | --- |
| Q3（`migration-plan.md` §11 已登记） | `policy.require_type_unchanged` 生产代码无调用者，真正生效的是 `outline.py` 的 `_op_update_kp` | 本 change 在 `OutlineBuilder#updateKp` 内直接实现拒绝，与 `outline.py` 一致；`0004` 的 `requireTypeUnchanged` 保持纯函数保留 |
