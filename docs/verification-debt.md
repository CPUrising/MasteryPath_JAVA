# 验证债台账（verification-debt）

> 把「写了但没跑过」的验证显式记账，防止把未验证代码当成已通过。
> 状态定义：`未执行` / `执行失败` / `通过` / `阻塞`。
> 结算规则：每一行必须由真实执行结果关闭；关闭时把证据链接写进 `docs/verify/`。

| 模块 | 应测项 | 测试文件 | 状态 | 备注 |
| --- | --- | --- | --- | --- |
| 工程骨架 | 上下文装配 + 包分层 | `AppContextTest`、`PackageLayoutTest` | 通过 | 3 用例，见 [verify/0001](verify/0001-java-skeleton.md) |
| 领域模型 | TC-A1…A6 + 字段契约 + 跨语言读取 | `KnowledgePointTest` 等 5 个类 | 通过 | 21 用例，见 [verify/0002](verify/0002-domain-model.md) |
| 存储与 CAS | TC-B1…B17（含 6 个真实并发用例） | `LearningStoreTest`、`LearningStoreConcurrencyTest` | 通过 | 23 用例，见 [verify/0003](verify/0003-sqlite-store-cas.md) |
| 存储兼容 | 打开 Python 真实建出的库 + DDL 列名比对 | `ReferenceWorkspaceCompatibilityTest` | 通过 | 4 用例，见 [verify/0003](verify/0003-sqlite-store-cas.md) |
| 掌握策略 | TC-C1…C17、TC-F1、TC-F6/F7/F8 + 衰减方向 + 分支优先级 | `MasteryPolicyTest`、`MasteryScoreTest`、`PolicyOrderingTest`、`GoalPreservationTest` | 通过 | 38 用例，与参考实现 `pytest tests/test_policy.py` 21 passed 对照一致，见 [verify/0004](verify/0004-mastery-policy.md) |
| 模式门控 | TC-D1…D9 + 拒绝文案 + 预算常量 | `ModeGateTest`、`ToolClassificationTest`、`SessionModeTest`、`BehaviorBudgetTest` | 通过 | 42 用例，与参考实现 `pytest tests/test_mode.py` 12 passed 对照一致，见 [verify/0005](verify/0005-mode-gating.md) |
| 模式持久化 | TC-L1…L4（会话 mode 写入、`session_meta`、被拒不持久、重开恢复） | 待建 `SessionModeTest`（service 层） | 未执行 | 需 `setSessionMode`，属 `0006`；REST 端点属 `0015` |
| 结构变更门控 | TC-M2（study 下 `mastery_revise` 被拒且地图不变） | 待建 `MasteryToolsTest` | 未执行 | 需 `revise` 工具本体，属 `0007` |
| 回合预算 | TC-M6（单回合工具调用超限触发 `turn_end`） | 待建 `AgentLoopTest` | 未执行 | 需回合循环，属 `0013`；本仓库已提供 `BehaviorBudget` 常量 |
| 服务核心 | 路径生命周期 + 来源附加 + 声明掌握 + defer/已讲解 + 公共投影 | `PathLifecycleTest`、`SourceAttachmentTest`、`MasteryOverrideServiceTest`、`ObjectiveStateTest`、`QuestionCardProjectorTest` | 通过 | 55 用例，见 [verify/0006](verify/0006-service-core.md) |
| 路径删除级联 | messages/sessions/question_bank/interactions 级联，events 保留 | `PathDeletionTest` | 通过 | 8 用例，见 [verify/0006](verify/0006-service-core.md) |
| 学习者首间隔播种 | `review.scheduled` 事件 + provenance=learner 的 0.5 倍首间隔 | 待建 `SchedulerTest` | 未执行 | `overrideMastery` 的 `gate_cleared` 分支已返回布尔值，调度属 `0010` |
| 全部业务 | Java 实现尚未开始 | — | 未执行 | 从 `0007-outline-build-revise` 起；见 `docs/migration-plan.md` §5 |
| 参考实现对照 | 237 个 Python 用例的等价性 | `../MasteryPath/tests` | 未执行 | 按 change 逐组对照后关闭 |
| 参考实现行为瑕疵 | Q1–Q4（错误文案双套 / `objective.deferred` 缺 note / `require_type_unchanged` 无调用者 / review 下 grade 未校验掌握） | — | 照抄不改 | 均为参考实现原样行为，已逐字锁定；详见 `docs/migration-plan.md` §11，统一需另开 change |
| LLM 适配层 | 真实 provider 流式与 function calling | `OpenAiCompatibleClientTest` | 阻塞 | 需要真实 Key，属手工验证项 |
| 前端联调 | Next.js 对 Java 后端的端到端 | — | 阻塞 | 依赖 `0014`/`0015` 完成后进行 |
