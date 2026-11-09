# Tasks

## 1. 测试分类

- [x] 1.1 为 `com.masterypath.storage` 下 8 个测试类打 `@Tag("storage")`。
- [x] 1.2 为传输层/上下文用例打 `@Tag("api")`（`adapter/web` 10 个、`adapter/turn`、`service` 2 个、`settings`、`adapter/llm/TaskModelTest`、`AppContextTest`）。
- [x] 1.3 `scripts/verify.ps1` 改用 `-Dgroups` / `-DexcludedGroups`，并在头注释说明标签约定。

## 2. 零用例守卫

- [x] 2.1 分范围运行后统计本次新增的 surefire 报告；为 0 时输出 `[FAIL]` 并以退出码 3 结束；已用不存在的标签做过负向验证。

## 3. 临时工作目录

- [x] 3.1 新增 `TempWorkspace`（在 `target/test-workspace/` 下建独立子目录 + 注册 `masterypath.data-dir`）。
- [x] 3.2 `AppContextTest` / `CorsApiTest` / `WsTurnTest` 通过 `@DynamicPropertySource` 接入。
- [x] 3.3 `application.properties` 保留兜底值并更新注释。

## 4. 验证

- [x] 4.1 `.\mvnw.cmd -q -DskipTests test-compile` 通过。
- [x] 4.2 `-Scope storage/api/unit` 与 `-Scope all` 均通过：75 / 178 / 615 / 868。
- [x] 4.3 连续跑两次 `-Scope all`，无跨运行残留（改用构建目录后再次全量通过）。

## Workflow follow-up

- 追加 `docs/regression-log.md`。
- 新增 `docs/ai-collaboration/0030-test-hygiene.md` 留痕。
- 人类审查证据后执行 `openspec archive 0030-test-hygiene --skip-specs -y`。
