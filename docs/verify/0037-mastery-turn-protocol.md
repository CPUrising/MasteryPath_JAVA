# 验证报告：0037-mastery-turn-protocol

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0037-mastery-turn-protocol` |
| 执行时间 | 2026-11-10 13:49–13:54（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 命令 | `.\scripts\verify.ps1 -Change 0037-mastery-turn-protocol`；`npm run typecheck`；`npm run build` |

## 2. 变更摘要

- WS 回合新增 `mastery_answer` / `mastery_skip`；`message` 为空时用答案文本作为本轮用户消息。
- `TurnRunner` 在启动导师 LLM 前调用 `mastery_grade` / `mastery_skip_question`，把结果作为
  `tool_result` 事件先行发出（metadata 键 `mastery_grade` / `mastery_skip`），并注入会话历史
  （仅 role + content，跳过空正文，最多 20 条）。
- `StudyTools.Quiz` 在注册前打乱选择项、按位置重贴 A/B/C 标签，`expected_answer` 跟随正确正文。
- 题卡新增 `allow_free_text=true`；`mastery_grade` 结果补 `correct_label` / `correct_body` /
  `learner_answer` / `independent`。
- `mastery_grade.question_id` 变为可选（缺省取当前待答题）；`mastery_skip_question` 无参数。
- 前端 `lib/ws.ts` 增加两个请求字段；会话页作答/跳过改走 WS 并消费 `tool_result.metadata.mastery_grade`，
  不再调用 `POST /grade`。

## 3. 验证结果

```
[INFO] Tests run: 909, Failures: 0, Errors: 0, Skipped: 0
[PASS] 测试通过。
```

```
> tsc --noEmit            # 通过
> next build              # 通过，8 个路由生成
```

新增/更新用例：

| 用例 | 覆盖 |
|---|---|
| `TurnRunnerTest#gradesCardAnswerBeforeTheTutorSpeaks` | 事件序列 `tool_result → text_delta → turn_end`；metadata 含 `mastery_grade`；LLM 收到历史与答案 |
| `WsTurnTest#gradesCardAnswerOverTheSocket` | WS 解析 `mastery_answer`、先行判分、答案文本作为 user 消息 |
| `StatusAndQuizToolsTest#shuffledChoiceKeepsExpectedBodyAndAllowsFreeText` | 选项乱序后标签连续、`expected_answer` 跟随正文、`allow_free_text`、缺省题号判分 |
| `StatusAndQuizToolsTest#skipWithoutArgumentsClearsOpenQuestion` | 无参 `mastery_skip_question` 清空待答题 |
| `StatusAndQuizToolsTest#unparseableChoiceIsIncorrect` | 无法解析的选择题返回 `incorrect`（不再是 `ungraded` 失败） |
| `ToolSchemasTest#requiredParametersMatchExecutors` | `mastery_grade` 只要求 `answer`；`mastery_skip_question` 无必填 |

## 4. 行为变化

| 场景 | 变更前 | 变更后（DeepTutor 一致） |
|---|---|---|
| 学习者提交答案 | 前端单独 `POST /grade`，回合结束 | WS `mastery_answer`，服务端先判分再让导师回复 |
| 答案文本 | 仅用于判分 | 同时作为本轮 user 消息 |
| 跳过题目 | 前端本地移除卡片 | WS `mastery_skip`，服务端清空待答题并回执 |
| 会话上下文 | 导师每轮从零开始 | 注入最多 20 条历史消息 |
| 选择题选项 | 模型给出顺序原样展示 | 注册时打乱并重贴标签 |
| 题卡判定 | 卡外气泡，靠 REST 响应 | `tool_result` 事件 + 卡内 `correct_label` / `correct_body` |

## 5. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | REST `POST /grade` 仍保留，前端已不调用 | 低 | 避免影响其他调用方；后续按需清理 |
| D2 | 前端 `ungraded` 分支已不可达（0036 移除该结果） | 低 | 不影响构建，留待前端整理批次 |
| D3 | 判分失败被吞掉（best-effort），该轮不返回 `mastery_grade` | 低 | 与设计一致；导师仍可自行调用 `mastery_grade` |
| D4 | 历史只取 role/content，未带工具调用与题卡 | 低 | 与 DeepTutor 回合消息格式一致 |
| D5 | 判分 `tool_result` 先于 `session_meta` | 低 | 对象无待答题后快照正确反映状态 |

## 6. 结论

- [x] 建议归档（等待人类确认）
- [ ] 需修复后复验
- [ ] 需人类决策
