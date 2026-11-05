# Tasks

## 1. 题目修复

- [x] 1.1 `repairQuestion` 参数校验（事务外）：`action` / `reason`（截断 500）/ `correct` 的 `expectedAnswer`，三条文案逐字
- [x] 1.2 题号五级回退 + 知识点三级回退 + `unknown objective` 文案
- [x] 1.3 `void` 分支：attempts/evidence 作废、interaction→ABANDONED、仅清同题 pending、题库置 `voided`
- [x] 1.4 `correct` 分支：先改键再判分、`ungraded` 拒绝、open 降级、attempts 复活、证据只作废未作废行、追加新证据、interaction 含 `repaired:true`
- [x] 1.5 `recomputeAfterRepair` 三分支（未掌握→清除 / 已掌握且原本已掌握→不动 / 否则→播种）；定性类型不写分
- [x] 1.6 事件 `assessment.voided` / `assessment.corrected`（7 键）+ 返回值 8 键

## 2. 画像与快照

- [x] 2.1 `recordLearnerProfile`：白名单 6 键、清洗 2000 截断、`teaching_strategy` 枚举校验、合并、变更检测、无变更不发事件
- [x] 2.2 `getState`：19 键快照，`pendingQuestion` 走 `QuestionCardProjector`（`INV-07`）

## 3. 测试

- [x] 3.1 `RepairQuestionTest`（18 例）：void 清掌握与计划 + 题库标记；correct 重判恢复门槛；三条校验文案；题号回退；pending 不误清；已作废证据原因不覆盖；open 降级；ungraded 拒绝；事件与返回值形状
- [x] 3.2 `LearnerProfileTest`（13 例）：合并与可见；无变更不发事件；白名单外键丢弃；枚举校验；空字段拒绝；`updated_at` 写入
- [x] 3.3 `PathStateTest`（11 例）：19 键齐全；missing 路径；`counts` 汇总；`reviews` 派生字段；`INV-07` 投影；`session_count`

## 4. 验证

- [x] 4.1 `.\scripts\verify.ps1 -Scope unit -Change 0012-repair-profile` → 369 通过
- [x] 4.2 全量 `.\scripts\verify.ps1 -Change 0012-repair-profile` → 456 通过
- [x] 4.3 与参考实现对照（`pytest tests/test_repair_profile.py -q`）→ 5 passed；另用真实 Python 探针做跨语言数据等价验证
- [x] 4.4 回填 `docs/verify/0012-repair-profile.md`、`docs/regression-log.md`、`docs/traceability.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0012-repair-profile.md`、`README.md`、`docs/migration-plan.md` §5.2（拆分记录）
