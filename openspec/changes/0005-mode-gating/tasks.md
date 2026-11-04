# Tasks

## 1. 模式枚举与提示词

- [x] 1.1 `SessionMode` 枚举：`OUTLINE` / `STUDY` / `REVIEW` + `value()` + 声明顺序即 `MODES`
- [x] 1.2 每个模式携带提示词块 `instruction()`，逐字复刻参考实现 `MODE_INSTRUCTIONS`
- [x] 1.3 `enforced(Object)`：未知/空/`null` → `null`（不门控，`INV-09`）
- [x] 1.4 `normalize(Object)`：未知/空/`null` → `STUDY`（展示与准入用）

## 2. 分类表与守卫

- [x] 2.1 `ToolClassification.SHARED_TOOLS`：8 个共享工具
- [x] 2.2 `ToolClassification.OUTLINE_ONLY`：`mastery_build`、`mastery_revise`
- [x] 2.3 `ToolClassification.STUDY_REVIEW`：9 个工具
- [x] 2.4 `TOOL_MODES` 派生表（不含共享工具，共享性靠「不在表里」表达）
- [x] 2.5 `assertClassificationComplete()` / `assertClassificationComplete(Set<String>)`：重叠与未归类双检，fail-closed

## 3. 门控判定

- [x] 3.1 `ModeGate.toolIsAllowed(String, Object)`：未登记工具放行；已登记按 `enforced` 判
- [x] 3.2 `ModeGate.owningModes(String)`：按 `SessionMode` 声明顺序返回
- [x] 3.3 `ModeGate.wrongModeMessage(String, Object)`：逐字文案，含单引号模式名、`mastery_mode`、破折号
- [x] 3.4 `ModeGate.admissionError(Object, boolean hasOutline)`：按 `normalize` 判 `STUDY` 且无大纲

## 4. 行为预算

- [x] 4.1 `BehaviorBudget`：`MAX_TOOL_CALLS_PER_TURN=6`、`MAX_CONSECUTIVE_QUESTIONS=3`、`MAX_HINTS_PER_QUESTION=2`

## 5. 测试

- [x] 5.1 `ModeGateTest`：TC-D1…D8（含 `TC-D4` 三种未知输入、`TC-D7` 归属顺序）
- [x] 5.2 `ToolClassificationTest`：TC-D9 完整性 + 重叠检测 + 未归类 fail-closed
- [x] 5.3 `SessionModeTest`：`enforced` / `normalize` 语义差异、提示词块非空且互不相同
- [x] 5.4 `BehaviorBudgetTest`：三个常量数值锁定

## 6. 验证

- [x] 6.1 运行 `.\scripts\verify.ps1 -Scope unit --change 0005-mode-gating`
- [x] 6.2 运行全量 `.\scripts\verify.ps1 --change 0005-mode-gating`
- [x] 6.3 与参考实现对照 `pytest tests/test_mode.py -q`
- [x] 6.4 回填 `docs/verify/0005-mode-gating.md`、`docs/regression-log.md`、`docs/traceability.md`、`README.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0005-mode-gating.md`
