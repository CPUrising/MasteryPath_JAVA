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
| 全部业务 | Java 实现尚未开始 | — | 未执行 | 从 `0004-mastery-policy` 起；见 `docs/migration-plan.md` §5 |
| 参考实现对照 | 237 个 Python 用例的等价性 | `../MasteryPath/tests` | 未执行 | 按 change 逐组对照后关闭 |
| LLM 适配层 | 真实 provider 流式与 function calling | `OpenAiCompatibleClientTest` | 阻塞 | 需要真实 Key，属手工验证项 |
| 前端联调 | Next.js 对 Java 后端的端到端 | — | 阻塞 | 依赖 `0014`/`0015` 完成后进行 |
