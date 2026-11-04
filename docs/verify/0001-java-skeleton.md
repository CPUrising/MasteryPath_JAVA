# 验证报告：0001-java-skeleton

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0001-java-skeleton` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-04 12:09:10（本地时区 Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS（Temurin）、Maven 3.9.16（wrapper）、Spring Boot 3.5.16、sqlite-jdbc 3.54.1.0 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0001-java-skeleton` |
| 日志 | `artifacts/verify-0001-java-skeleton-20261104-120910.log` |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

本 change 声明 `skip_specs: true`（行为等价重写的工程骨架，不改变任何行为合同），因此不覆盖 `specs/` 下的业务需求。
仅验证 G0 的两个冒烟条件：

| Requirement | Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|---|
| —（G0 准入） | 应用上下文可装配 | `AppContextTest#contextLoads` | PASS | Spring Boot 3.5.16 启动成功，耗时 1.592s |
| —（G0 准入） | 运行于 Java 21+ | `AppContextTest#runsOnJava21` | PASS | `Runtime.version().feature() >= 21` |
| —（G0 准入） | 五个顶层包存在 | `PackageLayoutTest#allLayersExist` | PASS | `domain`/`storage`/`service`/`adapter`/`cli` |

合计：**3 个用例，3 PASS，0 FAIL，0 SKIP**。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 全量测试 | `mvnw.cmd -B -ntp test` | BUILD SUCCESS，Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 |
| 存储并发用例 | 尚不存在（`0003` 引入） | NOT RUN |
| 既有通过项是否回退 | 无既有基线（首个 change） | 不适用 |
| 参考实现对照 | 未执行 | NOT RUN：本 change 无业务逻辑，无可对照行为 |

## 4. 偏差与风险

| 编号 | 偏差 | 等级（阻断/严重/一般） | 处理 |
|---|---|---|---|
| D1 | 环境变量 `JAVA_HOME` 指向已不存在的 VS Code 扩展目录（`...redhat.java-1.55.0.../jre/21.0.11`），导致首次构建报 `JAVA_HOME is set to an invalid directory` | 一般 | 已在 `scripts/verify.ps1` 中加入 JDK 解析与回退（校验 `bin\javac.exe` 存在，回退到 `Program Files\Java` 下最高版本）。属环境健壮性改进，不改变任何行为合同 |
| D2 | `verify.ps1` 原版把构建输出的 stderr 当作终止错误，导致「测试 3/3 全绿但脚本退出码 1」 | 一般 | 已修正：构建段局部放宽 `ErrorActionPreference='Continue'`，只按 `$LASTEXITCODE` 判定。修正后重跑退出码 0 |
| D3 | `verify.ps1` 为无 BOM 的 UTF-8，PowerShell 5.1 按 ANSI 读取导致中文注释截断字符串 | 一般 | 已改为 UTF-8 with BOM |
| D4 | 脚本内局部变量 `$home` 与 PowerShell 只读自动变量 `$HOME` 冲突 | 一般 | 已重命名为 `$jdkDir` |
| D5 | 首次构建需联网下载 Spring Boot 3.5.16 与 sqlite-jdbc（本地仓库原本没有） | 一般 | Maven Central 可达（HTTP 200），已成功下载并缓存 |

> D1–D4 均发生在「验证入口」本身，不涉及业务代码。按规范，环境故障必须记录而不能掩盖——故在此逐条留痕。

## 5. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 3, Failures: 0` 来自 `artifacts/verify-0001-java-skeleton-20261104-120910.log`；结果包在 `target/surefire-reports/`。
