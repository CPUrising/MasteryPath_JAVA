# AI 协作留痕：0005-mode-gating

## 1. prompt 范围

- 授权修改的文件：`src/main/java/com/masterypath/domain/mode/**`（新增 4 个类 + `package-info`）、
  `src/test/java/com/masterypath/domain/mode/**`（4 个测试类）、
  `scripts/verify.ps1`（`-Scope unit` 补 3 个测试类名）、
  `openspec/changes/0005-mode-gating/**`、`docs/**`（证据回填）。
- 明确不做的事：不实现 `mastery_mode` 工具本体与会话持久化（`0006`）、
  不实现回合循环的预算消费（`0013`）、不实现 review 的 KP 级掌握校验（`0008`）、
  不实现 REST 模式切换端点（`0015`）。
- 出处：用户指令原文「git 提交后继续 0005」；`docs/migration-plan.md` §5 第 5 行。

## 2. AI 产出

- 新增文件：
  - `SessionMode`（枚举 + 提示词块 + `enforced` / `normalize` 两个归一函数）
  - `ToolClassification`（3 张静态表 + `TOOL_MODES` 派生表 + fail-closed 守卫）
  - `ModeGate`（`toolIsAllowed` / `owningModes` / `wrongModeMessage` / `admissionError`）
  - `BehaviorBudget`（6 / 3 / 2 三个常量）
  - 测试 4 个：`ModeGateTest`（21）、`ToolClassificationTest`（9）、
    `SessionModeTest`（8）、`BehaviorBudgetTest`（4）
- 修改文件：`scripts/verify.ps1`（`-Scope unit` 过滤补 `*ModeTest,*ModeGateTest,*BudgetTest`）
- 关键决策：
  - **两个归一函数必须分开**：`enforced` 未知值 → `null`（不门控，`TC-D4`）；
    `normalize` 未知值 → `study`（展示与准入）。合并任一必挂一个用例。
  - **共享性靠「不在 `TOOL_MODES` 里」表达**，不把共享工具写进三模式全放行的表里，
    否则 `TC-D9` 的「两集合不相交」失去意义。
  - `assertClassificationComplete` 抛 `IllegalStateException` 而非 `AssertionError`：
    Java `assert` 默认关闭，参考实现也是显式 `raise`（运行期契约校验）。
  - `SessionMode` 用枚举而非 `String`：`owningModes` 依赖 `MODES` 顺序，
    而 `wrongModeMessage` 取 `targets[0]` 作为建议切换目标，顺序错会让
    `mode='study'` 变成 `mode='review'`。
  - 错误文案逐字复刻，含 Python `repr` 的单引号与破折号 `—`。

## 3. 人工修正点

- 无人类介入（人类尚未审查本 change 产出）。
- AI 自我修正（提交前完成）：
  1. `package-info.java` 首次写入时多了一行重复的 `package` 声明 → 编译报
     `class, interface, enum, or record expected` → 删除重复行。属编辑错误，非设计问题。

## 4. 回归结果

- 用例数 / 失败数：本 change 新增 42 / 0；全量 130 / 0。
- 证据链接：[`docs/verify/0005-mode-gating.md`](../verify/0005-mode-gating.md)
- 日志：`artifacts/verify-0005-mode-gating-20261104-132405.log`
- 参考实现对照：在 `../MasteryPath` 执行 `pytest tests/test_mode.py -q` → **12 passed**，
  与 Java 侧 12 个语义对应用例逐条对齐（见验证报告 §3 对照表）。

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| 未知模式是否门控 | **不门控**（放行全部工具） | `INV-09` / `TC-D4`；未记录模式意味着引擎不知道当前意图，宁可放行也不误拒 |
| 未知模式在准入时如何判 | **当作 `study`** | `admission_error` 用 `normalize`；`admissionError("bogus", false)` 必须返回拒绝文案 |
| 两个归一函数能否合并 | **不能** | 合并会让 `TC-D4` 或 `TC-D5` 之一失效，二者语义方向相反 |
| 共享工具是否写进 `TOOL_MODES` | **不写** | 否则「未声明工具」与「显式共享」不可区分，`TC-D9` 不相交断言失去意义 |
| 守卫异常类型 | `IllegalStateException` | Java `assert` 默认关闭；参考实现是显式 `raise`，属运行期契约 |
| `owningModes` 是否用 `Set` 迭代 | **不用**，按枚举声明顺序 | `wrongModeMessage` 取 `targets[0]`，顺序不稳会让建议模式漂移 |
| 门控在装配期还是调用期 | **调用期** | 一回合的工具 schema 在首轮前一次性组装，全部 mastery 工具每回合都挂载 |
| 4 个导航工具是否算共享 | **本 change 不纳入** | 参考实现代码的 `SHARED_TOOLS` 不含它们（文档有出入）；留待 `0014` 落地时归类 |
| review 下 `mastery_grade` 是否拒绝未掌握 | **本 change 不实现** | 参考实现代码只对 quiz 与 assess 有该检查；`0008` 按代码行为实现并登记 |
| 预算常量是否可配置 | **不可** | ADR-019 锁定 6 / 3 / 2；`BehaviorBudget` 为 `static final int` |
