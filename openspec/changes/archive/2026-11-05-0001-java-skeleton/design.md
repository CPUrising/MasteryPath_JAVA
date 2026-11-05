# Design: Java 工程骨架与验证入口

## Context

- 本机已装 JDK 21.0.11，**未装 Maven、未装 Gradle**（已实测 `mvn -v` / `gradle -v` 均失败）。
- 参考实现是 Python，`pyproject.toml` 用 setuptools + pytest，命令简单（`pytest -q`）。
- 规范要求「唯一验证入口」与「证据由执行验证的机器产出」。

## Goals / Non-Goals

**Goals**
1. 任何人 clone 后只需 JDK 21，即可 `.\mvnw.cmd -q test` 通过。
2. `.\scripts\verify.ps1` 一条命令产出日志并落盘。
3. 包分层在第一天就固定，避免后续 change 反复搬包。

**Non-Goals**
1. 不做多模块 Maven 拆分（当前规模单模块足够；拆模块属性能/组织优化，需另开 change）。
2. 不配置 CI。
3. 不做代码格式强制（后续若需要，单独 change 引入 spotless）。

## Decisions

1. **用 Maven Wrapper 而非 Gradle Wrapper**
   - 选择：Maven 3.9 + `mvnw.cmd`。
   - 理由：Spring Boot 官方文档与绝大多数示例以 Maven 为主；团队心智成本低；wrapper 只需提交 4 个文件。
   - 放弃的方案：Gradle（Kotlin DSL 更现代，但本机无 Gradle，且 wrapper 引导脚本更长、发行包约 130MB，离线初始化更易失败）。

2. **版本锁定（Maven Central 实测，2026-11-04）**
   - `spring-boot-starter-parent`：3.5.16（3.5.x 最新）
   - `org.xerial:sqlite-jdbc`：3.54.1.0（最新）
   - 本机 `~/.m2/repository` **不含** 3.5.x 与 sqlite-jdbc，首次构建需联网；Maven Central 可达（HTTP 200）。
   - 本机 `~/.m2/wrapper/dists/` 已有 `apache-maven-3.8.6`，可作 wrapper 引导的离线兜底。

3. **单模块 + 包分层**
   - 选择：单模块 `com.masterypath`，子包 `domain` / `storage` / `service` / `adapter` / `cli`。
   - 理由：与参考实现的分层一一对应（§3 映射表）；单模块编译快；分层靠**包 + 测试约束**保证，而不是靠 Maven 模块强制。
   - 放弃的方案：多模块（`domain-module` / `app-module`），会在 19 个 change 里反复改 `pom.xml`。

4. **验证脚本用 PowerShell 而非 bash**
   - 选择：`scripts/verify.ps1`。
   - 理由：本机是 Windows + PowerShell 5.1；规范允许 `verify.ps1`（第 4 节）。
   - 兼容：脚本内部只用 `mvnw.cmd`，不依赖 Git Bash。

5. **冒烟测试的形态**
   - 选择：一个 `@SpringBootTest` 上下文加载测试 + 一个纯 JUnit 断言。
   - 理由：同时验证「Spring 能起」与「测试框架能跑」，比只写 `assertTrue(true)` 更有信息量。
   - 放弃的方案：只写纯 JUnit（无法证明 Spring 装配正确，而后续 `adapter` 层强依赖 Spring）。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| wrapper 首次运行需下载 Maven 发行包（约 9MB） | 无网络时 `mvnw` 失败 | 首次运行需网络；失败时在 `docs/verification-debt.md` 登记为「阻塞」，不得伪造通过 |
| Spring Boot 上下文测试启动慢（数秒） | 后续 L1 测试被拖慢 | L1 纯函数测试**不**用 `@SpringBootTest`；只有 `adapter` 层用 |
| 单模块下 `domain` 可能被误依赖 Spring | 分层腐化 | 在 `0002` 引入 ArchUnit 规则（或在 code review 清单中强制），本变更只固定目录 |

## Migration Plan

1. 生成 wrapper 并固定版本。
2. 建目录与最小类。
3. 写冒烟测试。
4. 写 `verify.ps1`。
5. 跑一次，回填 `docs/verify/0001-java-skeleton.md`。

## Open Questions

- 无（技术选型已由人类确认：Spring Boot 3.5 + Java 21 + Maven + SQLite/JDBC）。
