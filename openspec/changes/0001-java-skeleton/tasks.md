# Tasks

## 1. 构建骨架

- [x] 1.1 提交 Maven Wrapper：`mvnw`、`mvnw.cmd`、`.mvn/wrapper/maven-wrapper.properties`
- [x] 1.2 写 `pom.xml`：`spring-boot-starter-parent` 3.5.16、Java 21、依赖 `spring-boot-starter-web`、`spring-boot-starter-validation`、`org.xerial:sqlite-jdbc:3.54.1.0`、`spring-boot-starter-test`
- [x] 1.3 建包目录：`domain` / `storage` / `service` / `adapter` / `cli`（每个包一个 `package-info.java` 说明职责）
- [x] 1.4 写 `com.masterypath.App`（`@SpringBootApplication`）

## 2. 冒烟测试

- [x] 2.1 `AppContextTest`：`@SpringBootTest` 能加载上下文
- [x] 2.2 `PackageLayoutTest`：断言 5 个包存在（防止后续误删目录）

## 3. 验证入口

- [x] 3.1 写 `scripts/verify.ps1`：支持 `--scope all|unit|storage|api`、`--change <id>`，日志落 `artifacts/verify-<change>-<ts>.log`
- [x] 3.2 写 `.gitignore`：`target/`、`data/`、`artifacts/`、`*.db`、`.idea/`

## 4. 验证

- [x] 4.1 运行 `.\scripts\verify.ps1 --change 0001-java-skeleton`
- [x] 4.2 回填 `docs/verify/0001-java-skeleton.md`
- [x] 4.3 追加 `docs/regression-log.md` 一行
- [x] 4.4 更新 `README.md` 阶段看板（G0 → 通过）
