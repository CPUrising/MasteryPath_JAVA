# AI 协作留痕：0030-test-hygiene

## 1. prompt 范围

- 用户指令：「本地 Mock LLM开始然后，两个顺手该修的隐患」。
- 本 change 只处理两处测试基建隐患（Mock LLM 见 `0029-mock-llm-dev-server`）。
- 授权修改：
  - `scripts/verify.ps1`
  - `src/test/resources/application.properties`
  - 约 24 个测试类（仅新增 `import` 与类级 `@Tag`）
  - `AppContextTest` / `CorsApiTest` / `WsTurnTest`（新增 `@DynamicPropertySource`）
  - 新增 `src/test/java/com/masterypath/TempWorkspace.java`
- 明确不做：不改生产代码、不改任何断言语义、不并行化测试。

## 2. AI 产出

- 标签分类：
  - `@Tag("storage")`：`com.masterypath.storage` 下 8 个类；
  - `@Tag("api")`：传输层、`TurnRunner`、`ChatService` / `KnowledgeService`、`ProviderProbe`、`TaskModel`、三个 `@SpringBootTest`；
  - 其余默认归 `unit`。
- `verify.ps1`：`-Dgroups` / `-DexcludedGroups` 代替 50 项 `-Dtest` 白名单；新增零用例守卫（退出码 3）。
- `TempWorkspace`：`target/test-workspace/run-<uuid12>`，通过 `@DynamicPropertySource` 注入。

## 3. 人工修正点

1. **首版工作目录方案被推翻（实测发现）**：最初用 `Files.createTempDirectory("masterypath-test-")`
   + JVM 退出钩子删除。一次会话后系统临时目录残留 **12 个**目录——Windows 下 SQLite 句柄
   在钩子执行时尚未释放。改为 `target/test-workspace/run-<uuid>`：跨运行隔离不变，
   `mvnw clean` 可一次性收走，也不再污染系统临时目录。
2. **没有用 `${random.uuid}`**：`WebConfig` 有四个 `@Bean` 分别注入 `masterypath.data-dir`，
   占位符每次解析都会生成新值，会导致四个 bean 拿到四个不同目录。因此必须用
   `@DynamicPropertySource` 提供**同一个**已创建目录。
3. **批量注解外包**：24 个测试类的 `@Tag` 属机械改动，交由子代理执行（42 处匹配，无遗漏），
   完成后用 `mvnw -DskipTests test-compile` 复核。
4. **零用例守卫做了负向验证**：临时复制脚本并把 `-Dgroups=api` 换成不存在的标签，
   Maven 报 `Tests run: 0` + `BUILD SUCCESS`，脚本正确输出 `[FAIL]` 并返回 3；临时文件已删除。

未出现「放宽断言」「删除用例」「伪造验证结果」的情况。

## 4. 回归结果

- 分范围：storage **75** / api **178** / unit **615**，合计 **868**，均 0 失败。
- 全量连续三次：**868 / 0 / 0**（含改用构建目录后的最终实现）。
- 证据链接：[`docs/verify/0030-test-hygiene.md`](../verify/0030-test-hygiene.md)
- 日志：`artifacts/verify-0030-test-hygiene-20261109-*.log`

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| 用标签还是继续维护通配符 | **标签** | 未打标签默认进 unit（多跑），避免白名单漏配导致静默漏测 |
| `unit` 语义收紧是否可接受 | **可接受** | 只增不减；已多跑 `PackageLayoutTest`、`OutlineEvalCliTest` 等 |
| 是否直接改 `application.properties` 为随机值 | **否** | `${random.uuid}` 会被解析多次，四个 bean 目录不一致 |
| 是否保留 `target/test-workspace` 兜底 | **是** | 防止未来新增上下文写到仓库根目录 |
| 是否引入测试分组插件 | **否** | Surefire 原生支持 `groups`，无需新依赖 |
