# AI 协作留痕：0012-repair-profile

## 1. prompt 范围

- 授权修改的文件：`src/main/java/com/masterypath/service/LearningService.java`（+3 公开方法 +1 私有方法 + 4 个辅助）、
  `src/test/java/com/masterypath/service/RepairQuestionTest.java`、`LearnerProfileTest.java`、`PathStateTest.java`（均新增）、
  `scripts/verify.ps1`（`-Scope unit` 补 3 个测试类过滤）、`openspec/changes/0012-repair-profile/**`、`docs/**`。
- 明确不做的事：工具层 `MasteryRepairQuestionTool` / `MasteryProfileTool`（`0015`）、
  路径绑定 / scratch / 导航（`0013` / `0014`）、REST 端点（`0016`）。
- 出处：用户指令原文「提交后继续计划」；`docs/migration-plan.md` §5 第 12 行（拆分后）。

## 2. AI 产出

- 修改文件：`LearningService` —— `repairQuestion` / `recomputeAfterRepair` /
  `recordLearnerProfile` / `ProfileUpdate` / `getState` + 4 个私有辅助
  （`truncate` / `firstNonEmpty` / `round3` / `reviewTaskMap` / `gapMaps`）
- 新增测试：`RepairQuestionTest`（18）、`LearnerProfileTest`（13）、`PathStateTest`（11）
- 关键决策：
  - **`repairQuestion` 三项校验在事务外**：`action` → `reason`（先截断 500 再判空）→
    `correct` 的 `expectedAnswer`，三条文案逐字。
  - **题号五级回退逐字照抄**：注意「`question_id` 传了但查不到」**不会**回退到 open interaction。
  - **`correct` 必须先改答案键再判分**：顺序反了永远判错，修复无效。
  - **`correct` 复活 attempt（`voided=false`）而非新增**：历史只标记不删除。
  - **证据只作废 `!voided` 的行**：已作废的不覆盖原因，保住审计链。
  - **`recomputeAfterRepair` 与 `scheduleAfter` 不合并**：两者条件不同，参考实现也是两个函数。
  - **画像无变更不发事件**：重复提交同一份画像不应产生噪音。
  - **`null` 标签**：这里指画像的 `null` 值被跳过（与 `0011` 的标签 `null` → `"None"` 是两回事）。

## 3. 人工修正点

- **人类决策（开工前）**：原 `0012-repair-profile-nav` 实测 17 个任务、约 840 行，超
  `AGENTS.md` §3 的 8 个任务上限。AI 提出 3 种拆分方案，人类选择「拆成 3 个」。
  已记入 `migration-plan.md` §5.2，原 `0013`–`0019` 顺延为 `0015`–`0021`。
- AI 自我修正 3 轮：
  1. 编译错误：`PathStateTest` 用 `Map<?, ?>` 通配符无法传 String 参数 → 改为显式泛型强转。
  2. 测试写错：`ungradableCorrectionRejected` 用 `short` 题，任何答案键都能判分，不会 `ungraded`
     → 改用 `choice` 题 + 无法解析的作答 `zzz`。
  3. 文档笔误：`getState` 写成「21 个键」，Python 探针实测为 **19 个键** → 同步修正
     `design.md` / `tasks.md` / 测试注释。

## 4. 回归结果

- 用例数 / 失败数：本 change 新增 42 / 0；全量 456 / 0（414 → 456）。
- 证据链接：[`docs/verify/0012-repair-profile.md`](../verify/0012-repair-profile.md)
- 日志：`artifacts/verify-0012-repair-profile-20261104-191717.log`
- 参考实现对照：在 `../MasteryPath` 执行 `pytest tests/test_repair_profile.py -q` → **5 passed**。
- 跨语言数据等价：真实 Python 探针输出与 Java 断言逐条一致（见 verify 报告 §3）。

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| 是否把 0012 拆成 3 个 change | **是**（人类批准） | 17 个任务超上限 2 倍以上；写侧绑定与只读导航是两种行为增量 |
| `correct` 改键与判分的顺序 | **先改键再判分** | 顺序反了判的还是旧键，修复永远无效 |
| `correct` 对 attempt 是复活还是新增 | **复活**（`voided=false`） | 参考实现原样；历史不删除 |
| 已作废证据的原因是否覆盖 | **不覆盖** | 参考实现 `if not event.voided:` 守卫 |
| `recomputeAfterRepair` 是否复用 `scheduleAfter` | **不复用** | 两者条件不同；参考实现是两个独立函数 |
| 画像无变更是否发事件 | **不发** | 参考实现 `if not changed: return` |
| 画像白名单外键 | **静默丢弃** | 参考实现 `if key in allowed` 过滤 |
| `getState` 的 `pendingQuestion` 是否直接返回完整对象 | **不，走投影** | `INV-07`：不得含 `expectedAnswer` |
| `getState` 键数量 | **19** | Python 探针实测；初稿写 21 是笔误 |
| 工具层两个 `Tool` 类是否本 change 实现 | **不实现** | 属 `0015`；服务层用例已覆盖同样的校验分支 |
