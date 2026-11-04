# MasteryPath-Java

把 [MasteryPath](../MasteryPath) 的**后端**从 Python 重写为 Java（Spring Boot 3.5 + Java 21 + SQLite/JDBC）。
行为合同不变：判定标准是参考实现的 237 个验收用例与 `docs/` 中的 60 条不变量。

> 本仓库使用 **OpenSpec + SDD**。开工前必读 [`openspec/AGENTS.md`](openspec/AGENTS.md)。
> 迁移计划：[`docs/migration-plan.md`](docs/migration-plan.md)（**待人类批准**）。

## 阶段看板

| Gate | 语义 | 状态 | 证据 |
| --- | --- | --- | --- |
| G0 | 工程骨架可用 | **通过**（2026-11-04） | [verify/0001](docs/verify/0001-java-skeleton.md) · 3 用例 PASS |
| G1 | 规范与纯逻辑冻结 | 进行中（domain + storage + policy 已完成，待 mode-gating） | [verify/0004](docs/verify/0004-mastery-policy.md) · 88 用例 PASS |
| G2 | 核心业务闭环 | 未开始 | — |
| G3 | 大纲与质量 | 未开始 | — |
| G4 | 回合与工具 | 未开始 | — |
| G5 | 协议兼容 | 未开始 | — |
| G6 | 知识库与设置 | 未开始 | — |
| G7 | 验收 | 未开始 | — |

## 目录

```
openspec/                  规范与变更的单一真实源
  config.yaml              语言与 schema
  project.md               目标 / 边界 / 技术栈 / 工程标准
  AGENTS.md                AI 操作规程（人机契约）
  specs/                   基线规范（实现阶段只读）
  changes/                 增量变更沙箱（0001 起）
docs/
  migration-plan.md        迁移计划（范围论证 / 架构映射 / Gate / change 清单）
  traceability.md          验收场景 ↔ 规范 ↔ Java 测试
  reference-env.md         参考环境锁定表
  regression-log.md        回归基线（只增不改）
  verification-debt.md     验证债台账
  verify/                  每个 change 的验证证据
  ai-collaboration/        AI 协作留痕
scripts/verify.ps1         唯一验证入口
src/main/java/com/masterypath/
  domain/                  模型 + 纯策略 + 判分 + 调度 + 门控（零 I/O）
  storage/                 SQLite + CAS + 事件
  service/                 业务编排
  adapter/                 LLM / Loop / TurnRunner / REST / WS / 工具
  cli/                     命令行入口（大纲评测）
```

## 命令

```powershell
.\mvnw.cmd -q -DskipTests compile          # 编译
.\mvnw.cmd -q test                          # 全量测试
.\scripts\verify.ps1 --change <change-id>   # 唯一验证入口（产出证据）
```

## 红线

1. 不扩大范围：顺手优化另开 change。
2. 不跳步：Plan → Check → Build → Verify → Merge。
3. 不伪造证据：`NOT RUN` 就是 `NOT RUN`，进 `docs/verification-debt.md`。
4. 不放宽断言：失败要么修实现，要么证明测试写错。
5. 不绕过变更改基线：行为变化走增量规范，归档时合并。
