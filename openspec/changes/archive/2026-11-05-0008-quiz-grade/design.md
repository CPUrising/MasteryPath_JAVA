# Design: 出题与判分

## Context

参考实现分两层，职责边界必须保持：

| 层 | 文件 | 职责 | 依赖 |
| --- | --- | --- | --- |
| 纯函数 | `learning/grading.py` | 把 `(题目, 作答)` 变成 `Decision` | 无 |
| 服务 | `service.register_question` / `grade` | 单槽位、幂等、证据、掌握度重算 | store + policy + grading |

`grading.py` 的第一原则写在文件头：**an unparseable answer is never graded wrong**。

## Goals / Non-Goals

**Goals**

1. 逐函数复刻 `normalize` / `similarity` / `resolve_choice` / `grade`，包括
   NFKC、casefold、CJK 去空格、标点剥离。
2. 单槽位语义：已有未终态题目时再注册返回 `null`（不抛错、不产生新题）。
3. 判分幂等：已 `graded` 时返回首次结果 + `status=already_graded`，证据不增加。
4. `ungraded` 时零状态变更（`INV-46`）。
5. `open` 的 `correct` 在服务层降级为 `partial`（`INV-35`）；有提示的 `correct` 也降级（`INV-36`）。

**Non-Goals**

1. 不实现复习调度（`0010`）：只算 `gateCleared`，不播种 `repetitionStates`。
2. 不实现 `assess`（`0009`）与 `repairQuestion`（`0012`）。
3. 不实现工具层的 `ToolResult` 包装与 mode 门控调用（`0013`）；但 `QuizSpec` 的
   校验逻辑放在纯函数层，供工具层直接复用。

## Decisions

1. **`Grading` 用静态方法，`Decision` 用 record**
   - 理由：参考实现是模块级函数 + dataclass；无状态。
   - `Decision(String result, String reason, String resolved)`，其中 `resolved` 只有 choice 用。

2. **`normalize` 的顺序不可调整**
   - 参考实现：`NFKC` → `casefold` → `strip` → 折叠空白 → 剥离首尾标点 → 若含 CJK 则删所有空格。
   - 顺序影响结果：先剥标点再折叠空白，会让 `"a 。 b"` 与 `"a b"` 不等价。
   - Java 对应：`Normalizer.normalize(NFKC)` → `toLowerCase(Locale.ROOT)` →
     `trim` → `replaceAll("\\s+", " ")` → 剥首尾标点 → CJK 判正则后 `replace(" ", "")`。
   - 注意：Python 的 `str.casefold()` 与 Java 的 `toLowerCase(Locale.ROOT)` 对德语 `ß`
     等少数场景不同。**照抄 casefold 的语义**：用 `toLowerCase(Locale.ROOT)` 并在
     文档中登记该差异（实测本项目测试数据不含此类字符）。

3. **`similarity` 用 Levenshtein，阈值 `>= 0.90`**
   - 参考实现：`1 - distance / max(len)`，两者皆空返回 `1.0`。
   - 边界：`similarity("abcdefghi", "abcdefghij") == 0.9`，判 `correct`（`>=`）。

4. **长作答只做精确匹配**
   - 参考实现：`len(target) > 30 or len(learner) > 30` → 直接 `incorrect`。
   - 注意判的是**归一化后**的长度，不是原始长度。
   - 理由：长文本的编辑距离噪声大，模糊匹配会误判。

5. **数字等价用相对容差**
   - 参考实现：`abs(left - right) <= 1e-6 * max(1.0, abs(right))`。
   - 照抄；`2` vs `2.0` 判 `correct`，`2` vs `3` 判 `incorrect`。

6. **choice 的正文匹配必须唯一**
   - 参考实现：`resolve_choice` 返回**唯一**命中；多个选项正文相同则返回 `""` → `ungraded`。
   - 理由：正文重复时无法确定学习者选了哪个，猜就是错判。

7. **open 的关键词规则**
   - 必需词未全中：全没中 → `incorrect`；中了一部分 → `partial`。
   - 必需词全中但可选词命中率 `< 0.60` → `partial`。
   - 都没有关键词 → `ungraded`（无法确定性判定）。
   - 照抄。

8. **服务层降级顺序：open 先，hints 后**
   - 参考实现顺序：
     1. `if open and result == correct: result = partial`
     2. `if hints_used > 0 and result == correct: result = partial`
   - 两步互不干扰（第二步进入时 `result` 已非 `correct`），但顺序照抄。
   - 注：参考实现还有第三步 `hints_used > MAX_HINTS_PER_QUESTION`，因第二步已覆盖，属**冗余分支**。
     Java 侧照抄以保持行为一致，并登记为已知冗余。

9. **掌握度只在定量类型上重算**
   - 参考实现：`if kp.type not in QUALITATIVE_TYPES: mastery_levels[kp.id] = compute_mastery(active_attempts)`
   - 理由：定性类型的分数由 `assess` 打开，判分不能伪造分数（`INV-05`）。
   - 后果：对 concept 判分永远 `mastery_after == 0.0`、`gate_cleared == false`。

10. **单槽位检查在事务外与事务内各做一次**
    - 参考实现：先 `store.get_open_interaction` 短路（快路径），事务内再查一次（防竞态）。
    - 照抄。

11. **`gateCleared` 在追加 attempt 与证据之后计算**
    - 顺序：追加 attempt → 追加 evidence → 重算 mastery → `gateCleared = isMastered && !wasMastered`
    - 理由：`isMastered` 读的是刚写入的分数与证据；先算会恒为 false（与 `0006` 的 override 同类陷阱）。

12. **题库条目在判分时写入**
    - 参考实现：`tx.save_bank_entry(QuestionBankEntry(...))`，`entry_id` 为随机 hex。
    - Java 侧用 `UUID.randomUUID().toString().replace("-", "")`，与参考实现长度一致（32 位 hex）。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| `casefold` 与 `toLowerCase` 差异 | 德语 `ß` 等极少数场景判分不同 | 登记到 `migration-plan.md` §11；测试数据不含此类字符 |
| `normalize` 步骤顺序被改 | `"a 。 b"` 与 `"a b"` 判分不同 | 用例覆盖标点 + 空白组合 |
| `gateCleared` 先算后写 | 永远 `false`，掌握永不打开 | 用例断言「首次答对 → `gate_cleared=true`」 |
| `ungraded` 仍写证据 | 猜测被记成负面（`INV-46` 违规） | 用例断言 `ungraded` 后 `masteryLevels` 与 `learningEvidence` 均无变化 |
| 幂等分支漏判 | 重复判分产生重复证据 | 用例断言第二次返回 `already_graded` 且证据仍为 1 条 |
| open 的 `correct` 未降级 | 开放题单独打开门槛（`INV-35` 违规） | 用例断言 open 判 `correct` 时服务层返回 `partial` |
| 定性类型被写入分数 | 判分伪造定性通过（`INV-05` 违规） | 用例断言 concept 判分后 `masteryLevels` 无该 KP |

## Migration Plan

1. `Grading` + `Decision`（纯函数，无依赖）。
2. `QuizSpec` 校验（依赖 `Grading.resolveChoice`）。
3. `LearningService.registerQuestion`。
4. `LearningService.grade`。
5. `LearningService.skipQuestion` / `listQuestions` / `tagQuestion`。
6. 测试：`GradingTest`、`QuizRegistrationTest`、`GradeFlowTest`、`QuestionBankTest`。
7. `verify.ps1 -Scope unit` → 全量 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/learning/grading.py` 与
  `capabilities/mastery/tools.py` 的 `_normalize_quiz` 为唯一依据。
- 已知参考实现测试空白（本 change 会补上）：
  - `open` 题在服务层被降级为 `partial` 的组合断言不存在（`test_grading` 只测纯函数层）；
  - `concept` 判分后不写分数这一 `INV-05` 行为无专门用例；
  - `skipQuestion` 无直接测试。
