# Proposal: 大纲构建与修订

## Why

`0006` 交付了服务骨架，但路径无法拥有知识点——`build` / `revise` 未实现。
参考实现把这两个操作放在 `learning/outline.py`（567 行纯函数），是本项目**最复杂的纯逻辑**：

- `build` 是**整图替换**，非空路径必须显式 `confirm`，否则拒绝并列出将丢失的内容；
- `revise` 是**按 id 的差分操作**（8 种 op），未触动知识点的进度必须保留；
- 两者都要检出**缺口**（目标无来源 / 来源未被使用）并生成**覆盖报告**。

没有这一层，`0008` 无法出题（没有知识点），`0019` 无法验收大纲质量。

## What Changes

- 新增 `OutlineBuilder`（`domain/outline`）：
  - `build(progress, payload, confirm, sourceMap, selectedSourceIds)` → `BuildResult`
  - `revise(progress, ops, selectedSourceIds)` → `ReviseResult`
  - `computeCoverage(progress, selectedSourceIds)` → `CoverageReport`
  - `resolveGap(progress, gapId, action)` / `applyProposal` / `rejectProposal`
- 新增 `LearningService` 的大纲方法：`build` / `revise` / `resolveGap` / `getCoverage`
- 新增 `SourceResolver`：`{label: id}` 映射 + 基名别名（`knowledge/loader.py` 的 `build_source_map`）

## Capabilities

### New Capabilities

无新增能力；实现 `specs/learning-tools` 的「Build And Revise Are Different Operations」
与「Coverage Report On Generation」两条需求的全部场景，以及 `specs/learning-domain`
的「Gap Classification」中缺口分类与消解部分。

### Modified Capabilities

无。

## Impact

- 影响文件：`src/main/java/com/masterypath/domain/outline/**`（新增 5 个类）、
  `src/main/java/com/masterypath/service/LearningService.java`（补 4 个方法）、
  `scripts/verify.ps1`（补测试类过滤）。
- 影响测试：无既有测试被修改。
- 明确不做（Out of scope，登记到 `docs/verification-debt.md`）：
  - `eval_outline`（覆盖比/接地比/无环的阈值判定与 CLI）→ 属 `0018`。
  - `propose_objective` / `note_gap`（捕获候选与缺口）→ 属 `0009`。
  - 扫描文件夹生成 `TopicSource` → 属 `0016`。
  - `reset`（`_reset_learning_state` 的独立入口）→ 属 `0012`；本 change 只在 rebuild 路径内部使用它。
