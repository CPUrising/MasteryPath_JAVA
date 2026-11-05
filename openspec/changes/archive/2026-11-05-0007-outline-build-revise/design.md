# Design: 大纲构建与修订

## Context

参考实现 `learning/outline.py` 是**纯函数模块**：无 I/O、无 LLM，接收 `LearningProgress` 与
payload，就地修改聚合并返回结果对象。三个关键约束：

| 不变量 | 语义 |
| --- | --- |
| `INV-40` | build/revise 的 payload 非法即拒绝，且**零状态变更** |
| `INV-41` | 非空路径的 `build` 必须 `confirm`，否则返回 `refused_rebuild` 且地图不变 |
| `INV-23` | 缺口必须**显式生成**并交用户选择，禁止静默重建或静默丢弃 |

## Goals / Non-Goals

**Goals**

1. 逐函数复刻，包括 8 种 revise op 的语义与错误文案。
2. `build` 的 id 分配规则（`m1`/`k1` 递增，跳过已存在）与**按名字解析前置**。
3. `revise` 的差分语义：未触动的知识点进度**逐字段保留**。
4. 缺口与覆盖报告的**确定性**计算（同输入必同输出）。

**Non-Goals**

1. 不做 LLM 大纲生成（模型只提供 payload，本层只校验与落地）。
2. 不实现 `eval_outline` 的阈值判定（`0018`）。
3. 不实现扫描文件夹（`0016`）。

## Decisions

1. **`OutlineBuilder` 用静态方法，不用实例**
   - 理由：参考实现是模块级函数；无状态就不需要注入。与 `MasteryPolicy`（`0004`）保持一致。

2. **`BuildResult` / `ReviseResult` 用 record**
   - 选择：`record BuildResult(String status, List<LearningModule> modules,
     List<SourceGap> gaps, List<String> dropped, boolean rebuilt, String message)`。
   - 理由：不可变返回值；字段固定；与参考实现的 dataclass 一一对应。

3. **`build` 的 id 分配顺序：先解析全部名字，再解析前置**
   - 参考实现两遍扫描：第一遍分配 `k1..kN` 并建立 `name → id`；第二遍解析 `prerequisites` 的名字。
   - 理由：前置可以**指向后面的知识点**（前向引用），一遍扫描无法解析。
   - 风险：若改成一遍，`TC-N4` 的环形前置用例会直接失败（因为找不到引用）。

4. **`build` 拒绝时**零状态变更**：先解析 payload，再判断 `hasContent && !confirm`**
   - 参考实现的顺序：`_parse_build_payload(...)` 先执行（可能抛 `OutlineError`），
     然后才判断 `has_content and not confirm`。
   - 选择：照抄。**这一点很关键**——若先判 `confirm` 再解析，非法 payload 会返回
     `refused_rebuild` 而不是抛错，行为就变了。

5. **`revise` 的 op 未知即抛错，且已应用的 op 不回滚**
   - 参考实现：循环内逐个 `handler(progress, op, result)`，任一 op 抛错就中断。
   - 由于外层 `store.mutate` 有事务，**整个 revise 会回滚**——本层不需要自己做回滚。
   - 选择：照抄（不在 `OutlineBuilder` 内加 try/catch）。

6. **`_drop_kp_state` 的清理范围必须逐项对齐**
   - 清 7 个 map（`masteryLevels` / `qualitativeMastery` / `knowledgeTypes` /
     `repetitionStates` / `learnerMasteryOverrides` / `deferredObjectives` / `explainedObjectives`）
   - 清 3 个 list 中该 KP 的元素（`quizAttempts` / `learningEvidence` / `reviewQueue`）
   - 理由：漏清任一项会让「已删除的知识点」继续影响掌握判定或复习队列。

7. **缺口 id 连续分配，且 build 时基于已有缺口数续号**
   - 参考实现 `build`：`_detect_gaps(modules, selected, len(progress.source_gaps))`
   - 参考实现 `revise`：`_detect_gaps(progress.modules, selected, 0)` ← **从 0 开始**
   - 选择：照抄这个不对称。`revise` 每次重算全部缺口，id 从 `g1` 起。
   - 理由：`revise` 会整体替换 `source_gaps`，续号会越滚越大。

8. **`computeCoverage` 的 `reported` 语义**
   - `reported = 有任一知识点声明了来源`。为 `false` 时 `missing` **必须为空**
     （否则会把全部来源都列为缺失，`TC-N3`）。
   - 选择：照抄。
   - 注意：`groundingRatio` 在无知识点时为 `1.0`（不是 0），避免空路径被误判为不合格。

9. **`SourceResolver` 的基名别名只在不唯一时放弃**
   - 参考实现：`{label: id}` + 当 `basename` 唯一时追加 `{basename: id}`。
   - 理由：模型可能只写文件名而不写完整路径；重名时不能猜。
   - 放在 `domain/outline` 而非 `service`：纯函数，无 I/O。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| `build` 先判 `confirm` 再解析 | 非法 payload 返回 `refused_rebuild` 而非抛错 | 用例断言「非空路径 + 非法 payload + 无 confirm」必须抛 `OutlineError` |
| 前置解析一遍扫描 | 前向引用失败，`TC-N4` 挂 | 两遍扫描；环形前置用例专门覆盖 |
| `revise` 的 `_drop_kp_state` 漏项 | 删除的 KP 仍影响掌握/复习 | 逐项断言 10 个字段都被清 |
| `revise` 缺口续号 | 缺口 id 越滚越大 | 照抄参考实现从 0 重算 |
| `reported=false` 时列 missing | `TC-N3` 挂 | 用例断言 `missing` 为空列表 |
| `groundingRatio` 空路径返回 0 | 空路径被判不合格 | 照抄返回 1.0 |

## Migration Plan

1. `SourceResolver`（纯函数，无依赖）。
2. `BuildResult` / `ReviseResult` / `OutlineError`（纯数据）。
3. `OutlineBuilder.build` + `computeCoverage` + 缺口检测。
4. `OutlineBuilder.revise` + 8 个 op。
5. `OutlineBuilder.resolveGap` / `applyProposal` / `rejectProposal`。
6. `LearningService.build` / `revise` / `resolveGap` / `getCoverage`。
7. 测试：`OutlineBuildTest`、`OutlineReviseTest`、`CoverageReportTest`、`GapClassificationTest`。
8. `verify.ps1 -Scope unit` → 全量 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/learning/outline.py` 为唯一依据。
- 已知参考实现测试空白（本 change 会补上）：
  - `revise` 的 `move_kp` / `reorder` / `remove_module` 无直接测试；
  - `_drop_kp_state` 的 10 个字段无逐项断言；
  - `applyProposal` / `rejectProposal` 属 `0009` 的 `test_capture.py`，但纯函数部分可在此单测。
