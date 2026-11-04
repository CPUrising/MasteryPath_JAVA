# AI 协作留痕：0004-mastery-policy

## 1. prompt 范围

- 授权修改的文件：`src/main/java/com/masterypath/domain/policy/**`（新增 3 个类）、
  `src/test/java/com/masterypath/domain/policy/**`（3 个测试类 + 1 个 fixture 工厂）、
  `src/test/java/com/masterypath/storage/GoalPreservationTest.java`（1 个测试类）、
  `scripts/verify.ps1`（仅补测试类过滤规则）。
- 明确不做的事：不实现模式门控、判分、SM-2 调度、工具层与 API；不改 `domain/model` 与
  `storage` 的既有契约；不引入 Spring 注解。
- 出处：`openspec/changes/0004-mastery-policy/proposal.md` §Impact；
  用户指令原文「只要继续 0004-mastery-policy」。

## 2. AI 产出

- 新增文件：
  - `MasteryScore`（近期加权正确率，衰减 0.6，跳过 `voided`）
  - `NextStep`（record，含 `toMap()` 供 REST 序列化）
  - `MasteryPolicy`（门槛、证据、来源、状态派生、可选性、下一目标、复习排序）
  - 测试 4 个：`MasteryPolicyTest`（21）、`MasteryScoreTest`（8）、
    `PolicyOrderingTest`（7）、`GoalPreservationTest`（2）
  - `PolicyFixtures`（测试数据工厂）
- 修改文件：`scripts/verify.ps1`（`-Scope unit` 过滤表达式补 `*PolicyTest,*OrderingTest,*ScoreTest,*GradingTest` 等）
- 关键决策：
  - `MasteryPolicy` 用**静态方法**而非 Spring `@Service`：参考实现是模块级无状态函数，
    用 Spring 会让 `domain` 依赖框架，违反硬性约束 1。
  - 门槛常量 0.9 与 `desired_retention` 0.9 **各自独立定义**，不复用（`04-scheduling.md` §七 明确要求）。
  - `eligible` 遇悬空前置 **fail-closed**（视为不满足），而不是忽略该前置。
  - `nextObjective` 的四个分支按固定顺序求值，顺序本身即语义，用 `PolicyOrderingTest` 逐对固化。
  - TC-C17（goal 保留）放在 `storage` 包：需要真实读写往返才能证明，纯函数层无法覆盖。

## 3. 人工修正点

- 无人类介入（人类尚未审查本 change 产出）。
- AI 自我修正（提交前完成）：
  1. `verify.ps1 -Scope unit` 只匹配到 `MasteryPolicyTest`（21 项），漏掉 `MasteryScoreTest`
     与 `PolicyOrderingTest` → 补全过滤规则。
  2. `PolicyOrderingTest` 初版用单字符知识点名 `"a"`/`"b"`，被 `0002` 的
     `knowledge point name must be at least 2 characters` 拒绝 → 改为 `alpha`/`beta`。
     这是名称校验正确生效的证据，未放宽任何断言。

## 4. 回归结果

- 用例数 / 失败数：本 change 新增 38 / 0；全量 88 / 0。
- 证据链接：[`docs/verify/0004-mastery-policy.md`](../verify/0004-mastery-policy.md)
- 日志：`artifacts/verify-0004-mastery-policy-20261104-130801.log`
- 参考实现对照：在 `../MasteryPath` 执行 `pytest tests/test_policy.py -q` → **21 passed**，
  与 Java `MasteryPolicyTest` 的 21 个用例一一对应（同 TC 编号、同输入、同断言）。

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| 掌握度衰减方向 | 最新作答权重 1.0，越早权重 `0.6^n` 递减 | 方向写反会「越努力越降分」且永远跨不过 0.9 门槛；由 `MasteryScoreTest#decayDirectionIsNotReversed` 固化 |
| 作废作答是否计入 | **不计入**（分子分母都跳过） | `INV-14`；只跳分子会人为压低分数 |
| 定性类型的门槛值 | 返回 1.0，**仅用于展示**，不参与推进判定 | `INV-18`/`TC-F1`；若把 1.0 当门槛，concept 永远无法推进 |
| 悬空前置 | **fail-closed**（视为不满足） | `INV-27`；历史数据被删后不能因此放行 |
| 声明掌握的来源优先级 | `system` > `learner` > `""` | `INV-01/03`；系统判定不能被人工声明掩盖 |
| 声明掌握是否写分/产证据 | **都不** | `INV-01`/`INV-15`；声明是标记，不是作答 |
| 模块阈值能否推进 | **不能**，只影响展示 | `INV-18`；模块阈值是聚合视图，不是门控 |
| 掌握门槛与复习保留率是否共用常量 | **不共用** | 两者语义不同，参考实现也是两个常量 |
| 四分支优先级 | 待答题 → 首个可选未掌握 → 到期复习 → complete | `TC-C11`/`TC-C9`；顺序即语义，用两两对照测试锁定 |
