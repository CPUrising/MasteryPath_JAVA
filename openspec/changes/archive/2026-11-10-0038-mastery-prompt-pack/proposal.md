# Proposal: mastery 提示词包与系统提示词装配

## Why

DeepTutor 的 mastery 回合不是「chat 提示词加一段叮嘱」，而是一套独立的提示词包
（`capabilities/mastery/prompts/zh/mastery_loop.yaml`）：身份、运行时策略、回合协议、
三种模式各自的职责、教学 playbook 分块装配。系统提示词由这些块按固定顺序渲染，
模式段落同时用于 `mastery_mode` 的返回值。

当前 Java 版的系统提示词是一段英文摘要（`AgentLoop.systemPrompt`），
`mastery_mode` 返回的 `instructions` 也是英文枚举常量，两者与 DeepTutor 的
导师协议差距很大：出题即收尾、冒号规则、模式请求、探测/先教策略等关键约束
都没有进入模型上下文。本 change 引入 DeepTutor 的提示词包与装配器。

## What Changes

- 新增资源 `prompts/zh/mastery_loop.yaml`（与 DeepTutor 逐字一致）、
  `prompts/zh/agentic_chat.yaml`（取其 `runtime_context` 与 `tool_call_policy` 兜底文案）、
  以及 `prompts/zh/hints/{rag,read_source}.yaml`（工具清单条目）。
- 新增 `MasteryPrompts`：解析 YAML，暴露 `general` / `runtime_policy` / `loop.system` /
  `loop.user` / `session.<mode>` / `playbook` / `finish_exhausted` / `settle_exhausted`，
  并渲染运行时日期、工具调用上限与工具清单。
- 新增 `MasteryPromptAssembler`：按 DeepTutor 顺序装配
  `mastery_tutor` → `mastery_session_mode` → `runtime_context` → `runtime_policy` →
  `mastery_loop` → `mastery_playbook` → `tool_call_policy` → `tools`，
  以 `## 块名` + `---` 分隔渲染，末尾追加语言指令。
- `TurnRunner` 在装配 LLM 前生成系统提示词并传给 `AgentLoop`；语言取设置
  `general.response_language`（缺省 `zh`）。
- `mastery_mode` 的 `instructions` 改为提示词包中的 `session.<mode>` 段落。

## Capabilities

### Added Capabilities

- `mastery-prompts`: 提示词包内容、装配顺序、渲染格式、模式段落与语言指令。

## Impact

- Java：新增 `adapter/prompt`（`MasteryPrompts`、`MasteryPromptAssembler`）；
  `TurnRunner`、`WebConfig`、`SharedTools`。
- 资源：`src/main/resources/prompts/zh/**`。
- 测试：新增 `MasteryPromptsTest`、`MasteryPromptAssemblerTest`；
  `TurnRunnerTest` 增加系统提示词断言。
- 行为：系统提示词与 `mastery_mode.instructions` 由英文摘要变为中文提示词包；
  模型上下文显著变长。
