# Proposal: Java 工程骨架与验证入口

## Why

参考实现（Python）已冻结全部行为合同，但本仓库还没有任何可编译的 Java 工程，也没有唯一验证入口。
在写任何业务代码之前，必须先证明「这套工具链能编译、能跑测试、能产出证据」，否则后续 18 个 change 都建立在未经验证的假设上。

本变更同时是规范第 14.13 条要求的「用第一个真实小变更完整走一遍五阶段闭环」。

## What Changes

- 提交 Maven Wrapper（`mvnw` / `mvnw.cmd` / `.mvn/wrapper/*`），使本机无需预装 Maven。
- 建立 Spring Boot 3.5 + Java 21 工程骨架与包分层：`domain` / `storage` / `service` / `adapter` / `cli`。
- 提供一个冒烟用例：应用上下文可加载 + 一个纯函数占位类可被单测。
- 建立唯一验证入口 `scripts/verify.ps1`（编译 → 分层测试 → 落日志 → 打印下一步）。
- 建立 `.gitignore`（排除 `target/`、`data/`、`artifacts/`、`*.db`）。

## Capabilities

### New Capabilities

无新增能力。本变更只搭工程骨架，不实现任何 `specs/` 下的需求。

### Modified Capabilities

无。

## Impact

- 影响文件：`pom.xml`、`mvnw*`、`.mvn/**`、`src/main/java/com/masterypath/**`（仅 `App` 与空包）、`src/test/java/**`（仅冒烟用例）、`scripts/verify.ps1`、`.gitignore`。
- 影响测试：无既有测试。
- 明确不做（Out of scope）：
  - 不实现任何领域模型、存储、服务、API（属 `0002` 及之后）。
  - 不引入 SQLite / Jackson 之外的业务依赖。
  - 不接前端、不写 Docker、不配 CI。
