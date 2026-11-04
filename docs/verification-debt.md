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
| 模式持久化 | TC-L1…L4（会话 mode 写入、`session_meta`、被拒不持久、重开恢复） | 待建 `SessionModeTest`（service 层） | 未执行 | 需 `setSessionMode`，属 `0006`；REST 端点属 `0017` |
| 结构变更门控 | TC-M2（study 下 `mastery_revise` 被拒且地图不变） | 待建 `MasteryToolsTest` | 未执行 | 需 `revise` 工具本体，属 `0007` |
| 回合预算 | TC-M6（单回合工具调用超限触发 `turn_end`） | 待建 `AgentLoopTest` | 未执行 | 需回合循环，属 `0015`；本仓库已提供 `BehaviorBudget` 常量 |
| 工具层：修复与画像 | `MasteryRepairQuestionTool` / `MasteryProfileTool`：服务层异常 → `ToolResult(ok=false)` 的**转换**（参考实现 `test_repair_tool_requires_reason_and_valid_action`、`test_profile_tool_records_partial_fields`） | 待建 `MasteryToolsTest` | 未执行 | 属 `0015`。**校验规则**已由 `RepairQuestionTest` / `LearnerProfileTest` 覆盖；**错误呈现方式**（抛异常 vs 返回 `ok=false`）未覆盖，见 [verify/0012](verify/0012-repair-profile.md) D5 |
| 服务核心 | 路径生命周期 + 来源附加 + 声明掌握 + defer/已讲解 + 公共投影 | `PathLifecycleTest`、`SourceAttachmentTest`、`MasteryOverrideServiceTest`、`ObjectiveStateTest`、`QuestionCardProjectorTest` | 通过 | 55 用例，见 [verify/0006](verify/0006-service-core.md) |
| 路径删除级联 | messages/sessions/question_bank/interactions 级联，events 保留 | `PathDeletionTest` | 通过 | 8 用例，见 [verify/0006](verify/0006-service-core.md) |
| 学习者首间隔播种 | `review.scheduled` 事件 + provenance=learner 的 0.5 倍首间隔 | `ReviewSchedulingTest#overrideSeedsLearnerTrack` | 通过 | 见 [verify/0010](verify/0010-scheduler-sm2.md) |
| 大纲构建与修订 | TC-J1…J6 + 8 个 revise op + dropKpState 全字段 | `OutlineBuildTest`、`OutlineReviseTest` | 通过 | 42 用例，见 [verify/0007](verify/0007-outline-build-revise.md) |
| 覆盖报告 | TC-N1…N4 + 接地比 + 无环检测 | `CoverageReportTest` | 通过 | 12 用例，见 [verify/0007](verify/0007-outline-build-revise.md) |
| 缺口分类 | TC-F2/F3/F4 + 服务层事件 | `GapClassificationTest` | 通过 | 15 用例，见 [verify/0007](verify/0007-outline-build-revise.md) |
| 大纲评测 | TC-N5/N6（覆盖比/接地比/无环阈值 + CLI 确定性） | 待建 `OutlineEvalTest` | 未执行 | 属 `0020` |
| 候选目标 | `propose_objective` / `note_gap`（捕获-延后） | 待建 `CaptureTest` | 未执行 | 属 `0009`；本仓库已实现 `applyProposal`/`rejectProposal` 纯函数 |
| 确定性判分 | TC-H1…H5 + normalize/similarity/resolveChoice | `GradingTest` | 通过 | 27 用例，见 [verify/0008](verify/0008-quiz-grade.md) |
| 出题校验 | TC-K1 全部校验分支 + 旧式选项解析 | `QuizSpecTest` | 通过 | 20 用例，见 [verify/0008](verify/0008-quiz-grade.md) |
| 判分流程 | TC-K3/K4/K5 + 幂等 + 掌握推进 + INV-05 | `GradeFlowTest` | 通过 | 23 用例，见 [verify/0008](verify/0008-quiz-grade.md) |
| 出题注册 | TC-K2 单槽位 + 连续追问预算 | `QuizRegistrationTest` | 通过 | 12 用例，见 [verify/0008](verify/0008-quiz-grade.md) |
| 题库 | 判分入库 + 知识点自动关联 + 标签（整体替换/去重/空白保留/清空）+ 筛选真值语义 | `QuestionBankTest` | 通过 | 14 用例，见 [verify/0011](verify/0011-question-bank.md)；修正 `0008` 的 3 处语义错误 |
| 题库 REST | `GET /questions`、`POST /questions/{id}/tags` | 待建 `QuestionBankApiTest` | 未执行 | 属 `0016` |
| 定性评估 | `assess`（concept/design 的 Feynman 通道） | `QualitativeAssessTest` | 通过 | 25 用例，见 [verify/0009](verify/0009-qualitative-assess.md) |
| 题目修复 | TC-R1/R2/R3：void 作废 + correct 重判 + 参数校验 | `RepairQuestionTest` | 通过 | 18 用例，见 [verify/0012](verify/0012-repair-profile.md) |
| 学习者画像 | TC-P1/P2：合并、白名单、枚举、无变更不发事件 | `LearnerProfileTest` | 通过 | 13 用例，见 [verify/0012](verify/0012-repair-profile.md) |
| 状态快照 | `get_state` 19 键 + `INV-07` 投影 | `PathStateTest` | 通过 | 11 用例，见 [verify/0012](verify/0012-repair-profile.md)；REST 端点属 `0016` |
| 路径概览与 scratch | 概览 9 键 / 跳过空 map / name 回退 / 升序；scratch id 派生、owner 认领、隐藏 | `PathBindingTest` | 通过 | 22 用例，见 [verify/0013](verify/0013-path-binding-scratch.md) |
| 会话绑定与删除 | 绑定改 `path_id`、空 session 短路；删除会话 + 三重条件清理空 scratch | `PathBindingTest` | 通过 | 见 [verify/0013](verify/0013-path-binding-scratch.md) |
| 工具层：路径导航 | `MasteryPathsTool` / `MasterySwitchTool` / `MasteryLeaveTool`：参数解析 + `ToolResult` 包装；**「切换到不存在的路径」的拒绝判定在工具层** | 待建 `MasteryToolsTest` | 未执行 | 属 `0015`。服务层语义已由 `PathBindingTest` 覆盖，见 [verify/0013](verify/0013-path-binding-scratch.md) D1/D2 |
| 回合内切换路径 | TC-S5（`test_turn_path_switch.py`）：一个 mastery 回合内切换并持久化绑定 | 待建 `WebSocketTurnTest` | 未执行 | 属 `0017`，需 WS 回合 |
| 路径 REST | `GET /learning/paths`、`DELETE /learning/sessions/{id}` | 待建 `LearningApiTest` | 未执行 | 属 `0016` |
| 复习调度 | TC-G1…G7（SM-2、播种、失败重学步、复习队列） | `SchedulerTest`、`ReviewSchedulingTest` | 通过（G6 部分） | 31 用例，见 [verify/0010](verify/0010-scheduler-sm2.md)；TC-G6 只断言公开签名，准入拒绝属 `0015`/`0016` |
| 复习设置 | `set_review_settings` 限幅 + 批量回写 state（TC-G7 端到端） | 待建 `ReviewSettingsTest` | 未执行 | 属 `0019` |
| 全部业务 | Java 实现尚未开始 | — | 未执行 | 从 `0014-mastery-nav` 起；见 `docs/migration-plan.md` §5 |
| 参考实现对照 | 237 个 Python 用例的等价性 | `../MasteryPath/tests` | 未执行 | 按 change 逐组对照后关闭（已完成 `test_policy`/`test_mode`/`test_outline`/`test_eval`/`test_grading`/`test_study`/`test_interactions`/`test_assess`/`test_scheduler`/`test_review`/`test_question_bank`/`test_repair_profile`/`test_path_binding`） |
| 参考实现行为瑕疵 | Q1–Q5（错误文案双套 / `objective.deferred` 缺 note / `require_type_unchanged` 无调用者 / review 下 grade 未校验掌握 / **`tag_question` 保留空白标签**） | — | 照抄不改 | 均为参考实现原样行为，已逐字锁定；Q5 由 `0011` 实测确认并锁定，详见 `docs/migration-plan.md` §11 |
| LLM 适配层 | 真实 provider 流式与 function calling | `OpenAiCompatibleClientTest` | 阻塞 | 需要真实 Key，属手工验证项 |
| 前端联调 | Next.js 对 Java 后端的端到端 | — | 阻塞 | 依赖 `0016`/`0017` 完成后进行 |
