# Design: 测试基建治理

## 1. 为什么用标签而不是继续维护通配符

| 方案 | 新增用例的默认归属 | 漂移风险 |
| --- | --- | --- |
| 现状：`-Dtest=*XxxTest,...` 白名单 | 不在名单 → **被跳过** | 高（悄悄漏跑） |
| 包路径模式 | 同包自动纳入 | 中（跨包用例仍需例外清单） |
| JUnit `@Tag` + `excludedGroups` | 未打标签 → **自动进 unit** | 低（默认更保守） |

选择标签的关键理由：**默认行为是「多跑」而不是「少跑」**。忘记打标签最多让 unit 变慢，
不会让用例消失；而白名单一旦漏配，验证结论直接失真。

标签划分：

| 标签 | 范围 | 判定依据 |
| --- | --- | --- |
| `storage` | `com.masterypath.storage` 全部用例 | 需要真实 SQLite 文件 |
| `api` | `adapter/web`、`adapter/turn`、`service/ChatServiceTest`、`service/KnowledgeServiceTest`、`settings/ProviderProbeTest`、`adapter/llm/TaskModelTest`、`AppContextTest` | 走传输层或完整 Spring 上下文 |
| 无标签 | 其余（domain / service / 工具 / 存储之外的纯逻辑） | `unit` 的默认集合 |

## 2. 零用例守卫

Surefire 在 `groups` 匹配不到时会正常退出，脚本会误报 `[PASS]`。因此在运行前记录时间戳，
运行后统计 `target/surefire-reports/TEST-*.xml` 中**修改时间不早于开始时间**的文件：

```
fresh == 0 且 scope != all  → [FAIL] 退出码 3
```

这条检查同时覆盖「标签拼错」「surefire 没识别 groups」两类配置事故。

## 3. 临时工作目录

`@SpringBootTest` 的数据目录由 `${masterypath.data-dir}` 决定，`WebConfig` 里被四个 `@Bean`
分别注入。**不能**直接在 `application.properties` 里写 `${random.uuid}`：该占位符每次解析都会
生成新值，四个 bean 会拿到四个不同目录。因此用 `@DynamicPropertySource`：

```java
static void dataDir(DynamicPropertyRegistry registry) {
    TempWorkspace.registerDataDir(registry);   // 每个 Spring 上下文一个目录
}
```

- 目录：`target/test-workspace/run-<uuid12>`，每次运行新建。
- 生命周期：随 `mvnw clean` 一起删除；不需要退出钩子。
- 为什么不用系统临时目录：实测在 Windows 上 JVM 退出时 SQLite 句柄尚未释放，
  `shutdownHook` 删除失败（一次会话残留 12 个目录）。放到构建目录既避免污染系统临时目录，
  又能被 `clean` 一次性收走。
- Spring 上下文缓存：同类用例复用同一上下文，因此也复用同一目录——这正是期望行为
  （同一上下文内需要共享状态），跨运行则完全隔离。

`application.properties` 的固定值保留为兜底：即便将来新增了未接入 `TempWorkspace` 的上下文，
也不会写到仓库根目录。

## 4. 兼容性

- 用例数量与断言不变，只改变分类与数据目录。
- `verify.ps1 -Scope all`（默认）行为不变：不带 groups 过滤，全量执行。
- `docs/verification-debt.md` 中的用例计数不受影响。
