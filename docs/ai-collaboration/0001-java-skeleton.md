# AI 协作留痕：0001-java-skeleton

## 1. prompt 范围

- 授权修改的文件：`pom.xml`、`mvnw*`、`.mvn/**`、`src/main/java/com/masterypath/**`（`App` + 5 个 `package-info`）、`src/test/java/**`（2 个冒烟测试）、`scripts/verify.ps1`、`.gitignore`。
- 明确不做的事：不实现任何领域模型、存储、服务、API（属 `0002` 及之后）；不接前端；不配 CI；不写 Docker。
- 出处：`openspec/changes/0001-java-skeleton/proposal.md` §What Changes / §Impact，人类于 2026-11-04 批准计划后授权开工。

## 2. AI 产出

- 新增文件：
  - `pom.xml`（Spring Boot 3.5.16 / Java 21 / sqlite-jdbc 3.54.1.0）
  - `.mvn/wrapper/maven-wrapper.properties`、`mvnw`、`mvnw.cmd`、`.mvn/wrapper/maven-wrapper.jar`
  - `src/main/java/com/masterypath/App.java` + `domain`/`storage`/`service`/`adapter`/`cli` 的 `package-info.java`
  - `src/test/java/com/masterypath/AppContextTest.java`、`PackageLayoutTest.java`
  - `scripts/verify.ps1`、`.gitignore`
- 关键决策：
  - 用 Maven Wrapper（`distributionType=only-script`，Maven 3.9.16），使本机无需预装 Maven。
  - 冒烟测试用 `@SpringBootTest` + 一个纯 JUnit，兼顾「Spring 能装配」与「测试框架能跑」。
  - `PackageLayoutTest` 用 `package-info.class` 资源存在性判断包是否存在，而非 `Package.getPackage`（Java 9 起废弃且对无类的包不可靠）。

## 3. 人工修正点

- 人类在计划阶段做出的修正：
  - 追问「Maven 与 wrapper 的区别」，据此在 `migration-plan.md` 新增 §1.5 说明取舍与本机实测。
  - 批准 Q1–Q4（Gate 重新映射、前端不改、先确定性检索、类名与 Python 模块一一对应）。
- 本 change 执行期间的自我修正（非人类介入，如实记录）：
  - 环境 `JAVA_HOME` 指向已删除目录 → 在 `verify.ps1` 增加 JDK 解析与回退。
  - `verify.ps1` 把构建 stderr 当终止错误 → 局部放宽 `ErrorActionPreference`，只按退出码判定。
  - `verify.ps1` 无 BOM 导致 PowerShell 5.1 中文截断 → 改为 UTF-8 with BOM。
  - 局部变量 `$home` 与只读 `$HOME` 冲突 → 改名 `$jdkDir`。

## 4. 回归结果

- 用例数 / 失败数：3 / 0（`AppContextTest` 2 项，`PackageLayoutTest` 1 项）。
- 证据链接：[`docs/verify/0001-java-skeleton.md`](../verify/0001-java-skeleton.md)
- 日志：`artifacts/verify-0001-java-skeleton-20261104-120910.log`
