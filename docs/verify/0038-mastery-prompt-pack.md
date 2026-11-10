# 验证报告：0038-mastery-prompt-pack

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0038-mastery-prompt-pack` |
| 执行时间 | 2026-11-10 14:05–14:11（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 命令 | `.\scripts\verify.ps1 -Change 0038-mastery-prompt-pack`；`npm run typecheck`；`npm run build` |

## 2. 变更摘要

- 新增提示词资源（与 DeepTutor zh 包逐字一致）：
  `prompts/zh/mastery_loop.yaml`、`prompts/zh/agentic_chat.yaml`、
  `prompts/zh/hints/{rag,read_source}.yaml`。
- 新增 `MasteryPrompts`：解析 YAML，提供 `general` / `runtime_policy` / `loop.system` /
  `loop.user` / `session.<mode>` / `playbook` / `finish_exhausted` / `settle_exhausted`，
  并渲染运行时日期、工具调用上限、工具清单与语言指令。
- 新增 `MasteryPromptAssembler`：块顺序
  `mastery_tutor → mastery_session_mode → runtime_context → runtime_policy →
  mastery_loop → mastery_playbook → tool_call_policy → tools`；
  `## 块名` + `---` 渲染，空块省略，末尾追加语言指令。
- `TurnRunner` 装配系统提示词并传给 `AgentLoop`；用户消息套用 `loop.user` 模板；
  响应语言取设置 `general.response_language`（缺省 `zh`）。
- `mastery_mode.instructions` 改用提示词包中的 `session.<mode>` 段落。
- `WebConfig` 把 `SettingsStore` 注入 `TurnRunner`。

## 3. 验证结果

```
[INFO] Tests run: 922, Failures: 0, Errors: 0, Skipped: 0
[PASS] 测试通过。
```

```
> tsc --noEmit            # 通过
> next build              # 通过，8 个路由生成
```

新增/更新用例：

| 用例 | 覆盖 |
|---|---|
| `MasteryPromptsTest`（8 个） | 身份/策略、三模式段落互异与未知归一、冒号规则、模式请求、日期渲染、工具上限、工具清单、语言指令 |
| `MasteryPromptAssemblerTest`（4 个） | 块顺序与 `---`、日期与语言指令、`- 无` 兜底、空包不产生空块 |
| `TurnRunnerTest#systemPromptComesFromThePack` | 系统消息含 `## mastery_tutor` / 当前模式段落 / 语言指令；用户消息套模板 |

## 4. 行为变化

| 场景 | 变更前 | 变更后（DeepTutor 一致） |
|---|---|---|
| 系统提示词 | 一段英文摘要 | 中文提示词包 8 块装配 |
| 出题即收尾 / 冒号规则 | 未进入上下文 | `loop.system` 明确写入 |
| 模式职责 | 英文枚举常量 | `session.<mode>` 中文段落（系统提示词与 `mastery_mode` 共用） |
| 运行时日期 | 未提供 | `runtime_context` 注入 `yyyy年M月d日` |
| 工具说明 | 仅 schema | 额外 `tools` 块（有 hint 的工具） |
| 语言约束 | 无 | 末尾语言指令，允许学习者要求切换 |

## 5. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `finish_exhausted` / `settle_exhausted` 已入包但未接线：预算耗尽仍直接 `turn_end` | 中 | 下一批次「预算耗尽收尾」 |
| D2 | 知识库参考块与 `mastery_status` 快照仍追加在系统提示词末尾；DeepTutor 把快照作为独立 user 消息、KB seed 放进末条 user 消息 | 中 | 留到消息装配批次 |
| D3 | `tool_call_policy` 的 `{limit}` 填本仓库单回合上限 6（DeepTutor 单条助手消息 15） | 低 | 保持与引擎实际限制一致 |
| D4 | playbook 提到的 `ask_user` 工具尚未挂载 | 中 | 单独 change 增加交互工具 |
| D5 | 提示词资源固定 `zh`；其它响应语言只改末尾语言指令 | 低 | DeepTutor 同样只有 `zh`/`en` 资产 |
| D6 | `tools` 块只带 `rag` / `read_source` 的 hint；`mastery_*` 在 DeepTutor 中也无 hint | 低 | 与参考实现一致 |
| D7 | `SessionMode.instruction()` 英文兜底保留给直接调用 `AgentLoop` 的路径 | 低 | 生产 mastery 回合已全部走提示词包 |

## 6. 结论

- [x] 建议归档（等待人类确认）
- [ ] 需修复后复验
- [ ] 需人类决策
