# 验证报告：0030-test-hygiene

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0030-test-hygiene` |
| 执行时间 | 2026-11-09 19:20–19:30（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 工具链版本 | JDK 21.0.11 / Maven Wrapper 3.9.16 / Spring Boot 3.5.16 / Surefire 3.5.6 |
| 运行环境 | 本地 |
| 命令 | `.\scripts\verify.ps1 -Scope {unit,storage,api} -Change 0030-test-hygiene`、`.\scripts\verify.ps1 -Change 0030-test-hygiene` |
| 回归日志 | `artifacts/verify-0030-test-hygiene-*.log` |

> 生产代码 0 改动（`git status --porcelain -- src/main` 无输出）；只改测试注解、测试资源与验证脚本。

## 2. 标签分类

| 范围 | 选择器 | 用例 | 测试类 |
|---|---|---|---|
| `storage` | `-Dgroups=storage` | 75 | 8 |
| `api` | `-Dgroups=api` | 178 | 16 |
| `unit` | `-DexcludedGroups=storage,api` | 615 | 48 |
| 合计 | — | **868** | 72 |

三档相加等于全量 868，说明标签划分既无遗漏也无重复。

## 3. 零用例守卫（负向验证）

把 `verify.ps1` 临时复制为 `scripts/_guard_probe.ps1`，仅把 `-Dgroups=api` 替换为
`-Dgroups=no-such-tag` 后执行（验证后已删除该临时文件）：

```text
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[PASS] 测试通过。
[FAIL] scope=api 没有产出任何新的测试报告，疑似标签或筛选条件配置错误。
guard_exit=3
```

判定：**PASS** —— 这正是旧脚本会误报假绿的场景（Maven 退出码 0、0 个用例），
新守卫将其判为失败并返回退出码 3。

## 4. 工作目录隔离

`@SpringBootTest` 现在每次运行在 `target/test-workspace/run-<uuid12>` 下建独立目录：

```text
run-27122e57d7ec  2026/11/9 19:29:49
run-4c1b207d1b59  2026/11/9 19:29:49
run-70ba3d97260d  2026/11/9 19:29:49
```

- 一次全量运行产生 3 个目录，对应 `AppContextTest` / `CorsApiTest` / `WsTurnTest` 三个上下文。
- 连续两次全量运行均为 **868 / 0 / 0**，无跨运行数据冲突。
- 首版实现用系统临时目录 + 退出钩子清理，实测残留 12 个目录（Windows 下 SQLite 句柄未释放），
  因此改为构建目录；残留目录已清理，`mvnw clean` 可一次性收走。

## 5. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 分范围 | `-Scope storage` / `-Scope api` / `-Scope unit` | 75 / 178 / 615，全部 0 失败 |
| 全量（第 1 次） | `.\\scripts\\verify.ps1 -Change 0030-test-hygiene` | 868 / 0 / 0 |
| 全量（第 2 次） | 同上 | 868 / 0 / 0 |
| 全量（改工作目录后） | 同上 | 868 / 0 / 0 |

## 6. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `-Scope unit` 语义从「白名单」变为「除 storage/api 之外的全部」，会多跑 `PackageLayoutTest`、`OutlineEvalCliTest` 等原先遗漏的用例 | 一般 | 属预期收紧，已在 proposal 声明；只增不减 |
| D2 | 新用例若不慎打了错误标签，仍会进入错误分组 | 一般 | 默认行为是「多跑」；错误标签只会让 unit 变慢，不会让用例消失 |
| D3 | 未打标签的纯 Spring 用例会被算进 unit（拖慢 unit） | 一般 | 现有 3 个 `@SpringBootTest` 均已打 `api` |
| D4 | `target/test-workspace/` 会在多次运行间累积 `run-*` 子目录 | 一般 | 位于构建目录，`mvnw clean` 可清除 |

## 7. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策
