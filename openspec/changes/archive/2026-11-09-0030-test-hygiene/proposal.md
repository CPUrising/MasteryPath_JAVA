# Proposal: 测试基建治理（scope 漂移与工作目录复用）

## Why

两处隐患会让「测试跑过了」这句话失真：

1. **scope 靠手工通配符维护**：`scripts/verify.ps1` 的 `-Scope unit/storage/api` 用
   `-Dtest=*PolicyTest,*OrderingTest,...` 这类手写清单选择用例。新增一个测试类只要名字
   不匹配清单，就会在分范围验证里**被静默跳过**，而脚本仍然打印 `[PASS]`。
   清单本身已包含约 50 个模式，维护成本与漏配风险都在上升。
2. **共享工作目录跨运行残留**：`src/test/resources/application.properties` 把
   `masterypath.data-dir` 固定为 `target/test-workspace`。三个 `@SpringBootTest` 用例
   共用同一个 SQLite 库，上一次运行留下的会话/路径会污染下一次运行——
   `WsTurnTest` 不得不用随机后缀规避（见该类第 68–79 行注释），这就是症状。

## What Changes

- **测试分类改为 JUnit 5 标签**，不再靠类名通配符：
  - `@Tag("storage")`：`com.masterypath.storage` 下的存储/兼容/删除用例。
  - `@Tag("api")`：REST/WS 传输层、服务层聊天/知识库、回合编排、提供方探测、Task Model，
    以及全部 `@SpringBootTest` 用例。
  - 未打标签的用例默认属于 `unit`（纯函数与领域/服务逻辑）。
- `scripts/verify.ps1`：
  - `unit` → `-DexcludedGroups=storage,api`；`storage` → `-Dgroups=storage`；`api` → `-Dgroups=api`。
  - 新增**零用例守卫**：分范围运行结束时，如果本次没有产出任何新的 surefire 报告，判失败（退出码 3），
    避免「选择器配错 → 0 个用例 → 假绿」。
- **测试工作目录改为每次运行独立**：
  - 新增 `src/test/java/com/masterypath/TempWorkspace.java`：每次运行在
    `target/test-workspace/run-<uuid>` 下建一个独立目录并注册为 `masterypath.data-dir`。
  - `AppContextTest` / `CorsApiTest` / `WsTurnTest` 用 `@DynamicPropertySource` 接入。
  - `application.properties` 保留固定值作为兜底，并更新注释。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。`skip_specs: true`：生产代码、REST/WS 契约、领域行为均不变，只改测试选择与测试数据隔离。

## Impact

- 修改：`scripts/verify.ps1`、`src/test/resources/application.properties`、约 25 个测试类的注解、
  3 个 `@SpringBootTest` 类。
- 新增：`src/test/java/com/masterypath/TempWorkspace.java`。
- 不受影响：`src/main/**`、前端、数据库结构与已有断言。
- 语义变化（有意为之）：`-Scope unit` 从「白名单」变成「除 storage/api 之外的全部」，
  因此会多跑 `PackageLayoutTest`、`OutlineEvalCliTest` 等原白名单遗漏的用例；只会更全，不会更少。
- 明确不做：不引入测试分组插件、不并行化测试、不改任何断言语义。
