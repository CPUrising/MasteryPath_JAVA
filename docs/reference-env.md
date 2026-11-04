# 参考环境锁定表（reference-env）

> 本表记录「用什么环境判定通过」。任何一项变更都会使历史验证证据失效，需在 `docs/regression-log.md` 记一行。

## 1. 本机（编辑 + 验证同一台机器）

| 项 | 值 | 采集方式 |
| --- | --- | --- |
| 操作系统 | Windows（PowerShell 5.1） | `$PSVersionTable` |
| JDK | Temurin 21.0.11+9-LTS | `java -version` |
| 构建工具 | Maven Wrapper（`mvnw.cmd`，随仓库提交） | `.\mvnw.cmd -v` |
| Spring Boot | 3.5.x（版本以 `pom.xml` 为准） | `pom.xml` |
| SQLite JDBC | `org.xerial:sqlite-jdbc`（版本以 `pom.xml` 为准） | `pom.xml` |
| Node | v24.18.0（仅用于前端联调，不参与后端验证） | `node -v` |
| 参考实现 | `../MasteryPath`（Python 3.14 + pytest） | `python -m pytest -q` |

## 2. 参考实现基线（对照物）

| 项 | 值 |
| --- | --- |
| 测试文件数 | 35 |
| 测试用例数 | 237 |
| 运行命令 | `python -m pytest -q`（在 `../MasteryPath` 下） |
| 用途 | Java 用例的判定标准；用于「行为等价」对照 |

> 说明：参考实现基线数字由本仓库初始化时统计得到，不作为「已通过」证据；每次做等价性对照时必须重新执行并记录实际输出。

## 3. 数据库与文件契约

| 项 | 值 |
| --- | --- |
| 工作区数据库 | `data/learning.db`（SQLite） |
| 表 | `paths` / `events` / `interactions` / `question_bank` / `sessions` / `messages` |
| 聊天库 | 同一文件内的 `sessions` / `messages`（与参考实现一致） |
| 设置文件 | `data/settings.json`（不入库） |
| 知识库目录 | `data/knowledge_bases/`（不入库） |

## 4. 离线约束

- 全部单元/集成测试 MUST 在**无网络、无 API Key** 条件下通过（使用假 LLM 客户端）。
- 任何需要真实模型的测试 MUST 标记为手工验证项并登记到 `docs/verification-debt.md`。
