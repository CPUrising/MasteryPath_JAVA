# Proposal: 题目修复与学习者画像

## Why

两块能力都属 `ADR-020`（可修复的评估 + 学习者画像），共享同一条原则：**历史永不删除，
只标记与追加**。

1. **题目修复**：判分是确定性的，但**题目本身可能错**——答案键写错、题面歧义。
   没有修复通道，一个错题会永久污染掌握度，学习者只能重建整条路径。
   `repair_question` 提供 `void`（作废）与 `correct`（改答案键重判）两个动作。
2. **学习者画像**：`build` 之前需要 intake（先验知识、目标水平、时间预算）。
   画像写入后由 `getState` 读出，供后续大纲生成与教学策略使用。

原计划把「题目修复 + 画像 + 路径绑定 + 导航」合成一个 change，实测约 840 行 / 17 个任务，
超过 `AGENTS.md` §3 的 8 个上限，已拆为三个（见 `migration-plan.md` §5.2）。
本 change 是其中第一个：**A 块 + B 块**。

## What Changes

- 新增 `LearningService.repairQuestion(...)`：`void` / `correct` 两分支
- 新增私有 `recomputeAfterRepair(tx, kp, wasMastered)`：重算分数 + 调和复习计划
- 新增 `LearningService.recordLearnerProfile(...)`：白名单合并写入 + 变更字段检测
- 新增 `LearningService.getState(pathId)`：完整快照（画像的验收出口，也是 `0016` REST 的公共依赖）

## Capabilities

### New Capabilities

无新增能力；实现 `specs/learning-tools` 的「Question Repair」与 `specs/learning-domain`
的「Learner Profile」需求（两块规范均未单列章节，行为以参考实现为唯一依据）。

### Modified Capabilities

无。`skip_specs: true`：本 change 补齐参考实现已有、而 `0001`–`0011` 尚未实现的能力，
不改变任何既有行为合同。

## Impact

- 影响文件：
  - `src/main/java/com/masterypath/service/LearningService.java`（+3 个公开方法 +1 个私有方法）
  - `src/test/java/com/masterypath/service/RepairQuestionTest.java`（新增）
  - `src/test/java/com/masterypath/service/LearnerProfileTest.java`（新增）
  - `src/test/java/com/masterypath/service/PathStateTest.java`（新增）
  - `scripts/verify.ps1`（`-Scope unit` 补 `*RepairTest`、`*ProfileTest`、`*PathStateTest`）
- 影响测试：既有 414 个用例**不修改**。
- 明确不做（Out of scope）：
  - 工具层 `MasteryRepairQuestionTool` / `MasteryProfileTool` → 属 `0015`（Agent Loop 的工具层）。
    参考实现的两个工具类各 34/49 行，只是把服务层异常转成 `ToolResult`。
    本 change 用服务层用例覆盖同样的校验分支（与 `0008` 把 TC-K6 推给工具层的先例一致）。
  - `test_chat_nav.py` 的端到端 → 依赖 `ChatService` + `AgentLoop` + 假 LLM，挂到 `0017`。
  - 路径绑定 / scratch / hand-off → 属 `0013` / `0014`。
  - `getState` 的 `sources` / `knowledge_bases` 投影细节 → 参考实现原样照抄；
    REST 层的字段裁剪属 `0016`。
