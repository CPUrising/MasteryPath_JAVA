# Design: mastery 提示词包与系统提示词装配

## 1. 资源布局

```
src/main/resources/prompts/zh/
  mastery_loop.yaml        # 与 DeepTutor capabilities/mastery/prompts/zh 逐字一致
  agentic_chat.yaml        # 与 DeepTutor agents/chat/prompts/zh 逐字一致（取兜底文案）
  hints/
    rag.yaml               # 与 DeepTutor tools/prompting/hints/zh/rag.yaml 逐字一致
    read_source.yaml       # 同上
```

只复制当前工具面实际用到的提示词条目：`mastery_*` 在 DeepTutor 中没有 hint 文件，
因此不进入 `tools` 块；`rag` / `read_source` 有 hint 文件且会按条件挂载。

## 2. 取值与渲染

`MasteryPrompts`：

| 方法 | 来源 |
|---|---|
| `general()` | `mastery_loop.yaml:general` |
| `session(mode)` | `mastery_loop.yaml:session.<mode>`（归一未知模式为 `study`） |
| `runtimePolicy()` | `mastery_loop.yaml:runtime_policy` |
| `loopSystem()` / `loopUser()` | `mastery_loop.yaml:loop.system` / `loop.user` |
| `playbook()` | `mastery_loop.yaml:playbook` |
| `finishExhausted()` / `settleExhausted()` | `mastery_loop.yaml:loop.*` |
| `runtimeContext(date)` | `agentic_chat.yaml:runtime_context`，`{datetime}` 填 `yyyy年M月d日` |
| `toolCallPolicy(limit)` | `agentic_chat.yaml:tool_call_policy`，`{limit}` 填上限 |
| `toolManifest(names)` | `hints/<name>.yaml` 的 `short_description` / `when_to_use` / `input_format` |

资源缺失或解析失败一律退化为空串，装配器跳过空块——提示词包坏掉不能拖垮回合。

工具清单格式复刻 `format_list_with_usage`：

```
- `rag` — 在用户挂载的某个知识库中检索相关内容。
    适用场景: ...
    参数格式: ...
```

## 3. 块顺序与渲染

```
mastery_tutor
mastery_session_mode
runtime_context
runtime_policy
mastery_loop
mastery_playbook
tool_call_policy
tools
```

渲染规则（复刻 `LoopPromptAssembler.render`）：

- 每个块写成 `## <name>\n<content.strip()>`；
- 块之间用 `\n\n---\n\n` 连接；
- 内容为空的块整块省略；
- 末尾追加语言指令（`append_language_directive`，允许用户覆盖）。

`tools` 块没有可用条目时写 `- 无`（复刻 `_fallback_empty_tool_list`）。

## 4. 语言

语言取设置 `general.response_language`（缺省 `zh`）：

- 提示词资源固定读 `zh`（与 DeepTutor 一样，只有 `zh`/`en` 资产，其它语言落 `en`；
  当前仓库只带 `zh`）。
- 语言指令按 `response_language` 生成：`zh` 用中文指令 + 「用户可要求改用其它语言」
  覆盖句；`en` 用英文指令；其它语言用英文骨架 + 语言标签。

## 5. 接线

- `TurnRunner` 在 `MasteryTools.mountedTools(ctx)` 之后生成系统提示词：
  `new MasteryPromptAssembler(prompts).systemPrompt(mode, tools.keySet(), language, today)`。
- 现有「快照 + 知识库参考块」继续作为 `extraContext` 追加在系统提示词之后
  （`AgentLoop` 的既有能力）。
- `SharedTools.Mode` 的 `instructions` 改为 `MasteryPrompts.shared().session(mode)`。
- `SessionMode.instruction()` 保留为 `AgentLoop` 无提示词时的英文兜底，不再用于
  mastery 生产路径。

## 6. 测试

- `MasteryPromptsTest`：包内各块非空、模式段落互异、包含 DeepTutor 关键约束
  （冒号规则、模式请求、`<think>`、`mastery_mode`）。
- `MasteryPromptAssemblerTest`：块顺序与分隔符；日期与语言指令；工具清单
  （`rag` 有条目、纯 mastery 工具为 `- 无`）。
- `TurnRunnerTest`：系统消息来自提示词包（含 `## mastery_tutor` 与当前模式段落）。
