# 验证报告：0035-quiz-generation-context

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0035-quiz-generation-context` |
| 执行时间 | 2026-11-10 00:10–00:22（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 命令 | `.\mvnw.cmd -q -Dtest=QuizSetTurnTest test`、`.\scripts\verify.ps1 -Change 0035-quiz-generation-context` |
| 运行环境 | 真实模型：临时工作区 `target/tmp-0034-real`，后端 `127.0.0.1:8083` |

## 2. 变更摘要

- `QuizSetTurn` 新增 `generationContext(LearningProgress)`：路径名、目标、模块与知识点名称/类型，
  知识点最多 12 个；不含答案、掌握度、证据。
- `QuizSetGenerator` 的用户提示追加 `Learning context` 段；系统提示要求题目面向上下文知识点。
- 存储、事件、投影与判分语义不变。

## 3. 验证结果

```
[INFO] Tests run: 894, Failures: 0, Errors: 0, Skipped: 0
[PASS] 测试通过。
```

新增用例：

| 用例 | 覆盖 |
|---|---|
| `QuizSetTurnTest#promptIncludesPathContext` | 提示含 `Path:` / `Goal:` / 知识点名与类型；不含 `expected_answer` / `mastery` |
| `QuizSetTurnTest#generatesQuizSetWithEvents` 等既有用例 | 事件顺序、答案隐藏、失败零落库、修复一次保持通过 |

## 4. 真实模型复验

同一路径 `e2e-quiz`（知识点「Java 线程的六种状态名称」，memory），用户只说
「请针对这个知识点生成 3 道练习题。」（不写主题）：

| 题号 | 题型 | 题干（节选） |
|---|---|---|
| 1 | choice | 「以下哪一组是 Java 中 Thread.State 枚举定义的六种线程状态？」 |
| 2 | short | 「一个线程尝试进入 synchronized 方法或代码块，但锁已被另一个线程持有…此时 Thread.State 是什么？」 |
| 3 | open | 「线程 A 执行 sleep(1000)；线程 B 调用 join() 且未设置超时…分别指出状态并说明理由」 |

与 `0034` 修复前的勾股定理题对比，题目已锚定路径知识点。

## 5. 回归

| 范围 | 结果 |
|---|---|
| 全量 `verify.ps1` | 894 / 0 / 0 |
| 存储 / REST / 前端 | 无改动 |

## 6. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 上下文只取前 12 个知识点，超长大纲不完整 | 低 | 足以覆盖当前学习目标；后续可分页注入或只注入未掌握目标 |
| D2 | 题目仍可能覆盖已掌握知识点 | 低 | 上下文包含全部知识点，生成质量由模型决定；不影响掌握闭环 |

## 7. 结论

- [x] 建议归档（等待人类确认）
- [ ] 需修复后复验
- [ ] 需人类决策
