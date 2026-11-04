# AGENTS.md

本仓库使用 OpenSpec + SDD。所有 AI 智能体必须按本文件执行。

## 0. 开工前必读

1. `openspec/project.md`（本次重写的目标/边界/技术栈）
2. `openspec/specs/`（基线规范）
3. `docs/traceability.md`（场景 ↔ 规范 ↔ 测试）
4. `docs/migration-plan.md`（阶段与 change 清单）
5. 需要判定行为时，读参考实现 `../MasteryPath/docs/` 的对应章节（只读，不修改）
6. 本文件

## 1. 角色与边界

- 人类：定义意图、批准 change、审查证据、决定归档。
- 智能体：读上下文、拆任务、写代码与测试、跑验证、报告偏差。
- 智能体没有权限：扩大范围、跳过测试、直接改基线规范、伪造验证结果。

## 2. 工作循环

| 阶段 | 命令 | 产物 |
|---|---|---|
| Plan | `openspec new change <id>` | proposal / design / tasks / delta spec |
| Check | `openspec validate <id> --strict` | 校验通过 |
| Build | apply 命令 | Java 源码 + JUnit 测试 + 勾选的 `tasks.md` |
| Verify | `.\scripts\verify.ps1 --change <id>` | 日志 + `docs/verify/<id>.md` |
| Merge | `openspec archive <id>` | 更新基线 |

## 3. 命名与规模

- change id：`NNNN-kebab-case`，单调递增，不复用。
- 一个 change 只含一个行为增量；超过 8 个任务必须拆分。
- Java 包 `com.masterypath.<layer>.<area>`，类名与参考实现模块一一对应（见 `docs/migration-plan.md` §3 映射表）。

## 4. 硬性技术约束

1. `domain` 层（模型 + 纯策略 + 判分 + SM-2 + 门控）**零 I/O、零网络、零 Spring**，可在无上下文条件下单测。
2. SQLite 表结构与参考实现 DDL **逐字一致**（`paths` / `events` / `interactions` / `question_bank` / `sessions` / `messages` / `knowledge_*`），以便直接用参考库做数据兼容回归。
3. path 聚合以 JSON 文本 + `version` 整数列实现 CAS；写操作必须走 `mutate(pathId, fn)`，禁止绕过。
4. 判分、门槛、门控、调度**不得调用 LLM**（`INV-32` 等）。
5. REST 路径、WS 事件名与 JSON 字段名与参考实现保持一致；改动即为行为变化，必须走增量规范。
6. 学习者可见投影不得含 `expected_answer` / `explanation`（`INV-07`）。

## 5. 验证与证据

- 唯一验证入口：`scripts/verify.ps1`。
- 每次验证必须产出 `docs/verify/<change>.md`，并追加 `docs/regression-log.md`。
- 失败先读日志；最多自愈 3 轮；禁止放宽断言。
- 参考实现的同名 Python 用例是**判定标准**：Java 用例必须能指出它对应哪条 `TC-*`。

## 6. 范围纪律

- 顺手优化必须单独成 change。
- 概率性功能（大纲生成、建议下一问）必须保留全链路留痕与回退路径。
- 测试数据必须为合成数据；不得调用真实付费模型跑测试。

## 7. 交接

- 编辑端不得声称「已验证」。
- 验证端只产出证据；发现缺陷按本文件修复后重跑并登记偏差。
