# Proposal: 服务核心

## Why

`0002`–`0005` 交付了领域模型、存储与两层纯函数（掌握策略、模式门控），但没有任何
**编排**：谁能创建路径、谁把「学习者声明掌握」写成 `learner_mastery_overrides`、
谁保证学习者看不到答案。参考实现把这一层放在 `learning/service.py` 的 `LearningService`。

没有这一层，`0007` 的 build/revise 无处挂载，`0008` 的出题无法注册到聚合上。

## What Changes

- 新增 `LearningService`（服务层，依赖 `LearningStore`）：
  - 路径生命周期：`createPath` / `rename` / `delete`
  - 来源附加：`attachSources`（整体替换）/ `setKnowledgeBaseSources`（只替换 KB 类）
  - 掌握声明：`overrideMastery`（不写分、不产证据）
  - 目标状态：`deferObjective` / `noteExplained`
- 新增 `QuestionCardProjector`（`domain/projection`）：学习者可见投影，7 个白名单字段
- 新增 `LearningStore.deletePath`：级联删除 messages → sessions → question_bank → interactions → paths
  （**不删 events**，事件表 append-only）

## Capabilities

### New Capabilities

无新增能力；实现 `specs/learning-domain` 的「Mastery Gate / 学习者声明」、
`specs/agent-turn` 的「学习者可见投影」与 `specs/learning-tools` 的「单槽位」中
**服务层与投影部分**。

### Modified Capabilities

无。

## Impact

- 影响文件：`src/main/java/com/masterypath/service/**`（新增 `LearningService`）、
  `src/main/java/com/masterypath/domain/projection/**`（新增 `QuestionCardProjector`）、
  `src/main/java/com/masterypath/storage/LearningStore.java`（补 `deletePath`）、
  `scripts/verify.ps1`（`-Scope unit` / `storage` 补测试类名）。
- 影响测试：无既有测试被修改。
- 明确不做（Out of scope，登记到 `docs/verification-debt.md`）：
  - `overrideMastery` 在 `gate_cleared` 时的 `review.scheduled` 事件与学习者首间隔 → 属 `0010`（SM-2 调度）。
  - `build` / `revise` / 缺口 / 覆盖报告 → 属 `0007`。
  - `registerQuestion` / `grade` / `assess` → 属 `0008`。
  - `listPathOverviews` / `getState` 的完整快照 → 属 `0014`（REST 需要时才定形）。
  - `ensureScratchPath` / `bindSessionPath` → 属 `0012`（导航）。
  - `recordLearnerProfile` → 属 `0012`（画像）。
