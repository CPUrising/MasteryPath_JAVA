# 验证报告：<change-id>

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | |
| 执行人 | |
| 执行时间 | |
| 机器 / 系统 | |
| 工具链版本 | JDK / Maven / Spring Boot |
| 运行环境 | 本地 / CI |
| 命令 | `.\scripts\verify.ps1 --change <id>` |
| 日志 | `artifacts/verify-<id>-<timestamp>.log` |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

| Requirement | Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|---|
| | TC-xx | `XxxTest#method` | PASS / FAIL / NOT RUN | |

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 全量单元测试 | `.\mvnw.cmd -q test` | |
| 存储并发用例 | `.\mvnw.cmd -q -Dtest=*ConcurrencyTest test` | |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | |
| 参考实现对照 | 在 `../MasteryPath` 跑 `pytest -q` | |

## 4. 偏差与风险

| 编号 | 偏差 | 等级（阻断/严重/一般） | 处理 |
|---|---|---|---|
| D1 | | | |

## 5. 结论

- [ ] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策
