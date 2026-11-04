# Design: 题目修复与学习者画像

## Context

参考实现三处（`learning/service.py`）：

| 方法 | 行号 | 行数 |
| --- | --- | --- |
| `repair_question` | 388–581 | 194 |
| `_recompute_after_repair` | 1119–1154 | 36 |
| `record_learner_profile` | 184–234 | 51 |
| `get_state` | 236–297 | 62 |

Java 侧 `domain/model` 与 `storage` 的字段**全部就位**（`QuizAttempt.voided` /
`LearningEvidence.voided`/`voidReason`/`quality`/`attemptCount` /
`LearningProgress.learnerProfile` / `Transaction.findBankEntriesByQuestion` 等），
本 change 只写服务层编排。

## Goals / Non-Goals

**Goals**

1. 逐分支复刻 `repair_question`：参数校验文案、题号解析顺序、void/correct 的全部写入、
   事件 payload 的 7 个键、返回值的 8 个键。
2. `recomputeAfterRepair` 的三分支与 `0010` 的 `scheduleAfter` **不共用代码**——
   参考实现就是两个独立函数，语义不同（见 Decisions 6）。
3. 画像写入的白名单、清洗、变更检测、事件 payload 逐字复刻。
4. `getState` 的 **19 个键**完整实现，供 `0016` REST 直接复用。

**Non-Goals**

1. 不做工具层（`MasteryRepairQuestionTool` / `MasteryProfileTool`）→ `0015`。
2. 不做路径绑定 / scratch / 导航 → `0013` / `0014`。
3. 不改 `LearningStore`（`mutateBankEntry`、`setSessionPath` 等已够用）。

## Decisions

1. **`repairQuestion` 的参数校验在事务外**
   - 参考实现：`action` / `reason` / `expected_answer` 三项校验都在 `fn` 之外，
     抛 `PreconditionError`，**不进入 `mutate`**。
   - 顺序敏感：先校验 `action`，再校验 `reason`，最后校验 `correct` 分支的
     `expected_answer`。三条文案逐字：
     - `action must be 'void' or 'correct'`
     - `a reason is required so the repair can be audited`
     - `correcting a question requires the new expected_answer`
   - `reason` 截断到 500 字符（`[:500]`），截断在**校验空值之前**。

2. **题号解析的五级回退顺序必须逐字照抄**
   ```
   question_id → getInteraction(question_id) → getOpenInteraction()
              → progress.pendingQuestion.questionId
              → 反向遍历 quizAttempts 取第一个 !voided 的 questionId
   若仍为空 → "no question is available to repair"
   ```
   - 注意第 2、3 步的**条件**：`if interaction is None and not requested:` 才查 open interaction。
     若 `question_id` 传了但查不到，**不会**回退到 open interaction（照抄）。
   - 第 4 步（`pending`）的 `pending` 是在事务开始时取的快照，不是实时读。

3. **知识点解析的三级回退**
   ```
   question.knowledgePointId → attempts[0].knowledgePointId → evidenceRows[0].knowledgePointId
   ```
   - 失败抛 `unknown objective: {kpId}`（与 `assess` 同文案，不是 `unknown knowledge point:`）。

4. **void 分支的五处写入**
   - attempts：`voided=true` + `voidReason=note`
   - evidence：`voided=true` + `voidReason=note`
   - interaction：`status=ABANDONED` + `saveInteraction`
   - pending：**仅当 `pending.questionId == resolvedId`** 才清空
   - bank entries：`isCorrect=false` + `result="voided"`
   - `repairedCorrect = null`（Java 用 `Boolean` 装箱，`null` 表示「不适用」）

5. **correct 分支的关键陷阱**
   - `userAnswer` 取 `attempts[0].userAnswer`，否则 `interaction.userAnswer`。
     `QuizAttempt.userAnswer` 在 Java 侧是 `Object` 类型，需 `String.valueOf`。
   - **先改答案键再判分**：`question.expectedAnswer = newExpected` 必须在 `Grading.grade` 之前，
     否则判的还是旧键。
   - `ungraded` 直接抛 `corrected answer cannot be graded: {reason}`。
   - `open` 的 `correct` → `partial`（与 `grade` 的 `INV-35` 同规则）。
   - attempts 是**复活**：`voided=false` + `voidReason=""`，不是新增。
   - evidence 只对 `!voided` 的行标记作废，`voidReason="answer key corrected: {note}"`；
     **已作废的不覆盖原因**。
   - 追加一条新证据，字段沿用 `prior = evidenceRows[-1]`：
     `assessmentType` / `hintsUsed` / `attemptCount` 沿用；
     `quality` 沿用（若 prior 为 null 则 `1.0`/`0.0`）；
     `sessionId`/`turnId` 用**本次调用**的，不是 prior 的。
   - interaction 的 `result` 是 6 键，含 `"repaired": true`。

6. **`recomputeAfterRepair` 与 `scheduleAfter` 不合并**
   - 两者语义不同：
     - `scheduleAfter`（`0010`）：按 `gateCleared` / `review` / 仅 touch 三分支推进；
     - `recomputeAfterRepair`：先重算分数，再按「未掌握 → 清除计划」「已掌握且原本已掌握 → 不动」
       「已掌握且原本未掌握 → 播种」三分支。
   - 共同点只有「播种」那一段。抽公共函数会让两个函数的条件交织，反而难验证。
   - 参考实现也是两个独立函数，照抄。

7. **`recomputeAfterRepair` 不写定性类型的分数**
   - `if kp.type not in QUALITATIVE_TYPES:` 才重算 `masteryLevels`。
   - 定性类型的掌握由 `qualitativeMastery` 决定，修复不伪造分数（`INV-05`）。

8. **`recordLearnerProfile` 的「无变更即不发事件」**
   - 参考实现：`changed` 为空时**直接返回** `(current, [])`，不写 `updated_at`、不发事件。
   - 这点很重要：重复提交同一份画像不应产生噪音事件。
   - 白名单外键**静默丢弃**（不报错），但若全部被丢弃则抛 `learner profile needs at least one field`。

9. **`teaching_strategy` 的枚举校验在事务外**
   - 只接受 `probe_first` / `teach_first`，否则 `teaching_strategy must be probe_first or teach_first`。
   - 校验发生在 `cleaned` 之后、`mutate` 之前。

10. **`getState` 是「读快照」，不是「读聚合」**
    - 返回 19 个键（已用真实 Python 探针核对），其中 `map` / `counts` / `next` / `reviews` /
      `due_reviews` 都是**派生**的。
    - `pendingQuestion` 必须走 `QuestionCardProjector`（`INV-07`：不得含 `expectedAnswer`）。
    - `path_revision` 类的字段用 `progress.version`，不加 1（与 `repairQuestion` 的
      `path_revision = version + 1` 不同——后者是「这次写完之后」的版本）。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 先判分再改答案键 | correct 永远判错，修复无效 | 用例断言「答 9 判错 → 改键为 9 → 变正确」 |
| void 时清空所有 pending | 误清别的目标的当前题 | 用例断言「不同 questionId 的 pending 不被清」 |
| correct 时覆盖已作废证据的原因 | 审计链断裂 | 用例断言已作废行的 `voidReason` 不变 |
| `repaired` 字段缺失 | 前端无法区分「修复后重判」与「首次判分」 | 用例断言 interaction.result 含 `repaired=true` |
| 画像重复提交产生噪音事件 | 事件流膨胀 | 用例断言「无变更时不发事件」 |
| `getState` 投影泄漏 `expectedAnswer` | `INV-07` 违规 | 用例断言 `pending_question` 的键集合为 7 个白名单 |
| 把 `recomputeAfterRepair` 与 `scheduleAfter` 合并 | 两个函数的条件交织，难验证 | 保持独立（Decisions 6） |

## Migration Plan

1. `repairQuestion` + `recomputeAfterRepair`（先写 void，再写 correct）。
2. `recordLearnerProfile`。
3. `getState`。
4. 测试：`RepairQuestionTest`、`LearnerProfileTest`、`PathStateTest`。
5. `verify.ps1 -Scope unit` → 全量 → `pytest tests/test_repair_profile.py` 对照 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/learning/service.py:184-297, 388-581, 1119-1154`
  为唯一依据。
- 参考实现测试空白（本 change 会补）：
  - `void` 时「不同 questionId 的 pending 不被清」无用例；
  - `correct` 时「已作废证据的原因不被覆盖」无用例；
  - `correct` 对 `open` 题的降级无用例；
  - `ungraded` 的拒绝对应文案无用例；
  - 画像「无变更不发事件」无用例；
  - 画像白名单外键被丢弃无用例；
  - `getState` 的 `INV-07` 投影无断言。
