# mastery-prompts Specification

## ADDED Requirements

### Requirement: Mastery Prompt Pack Blocks

系统提示词 SHALL 由 DeepTutor mastery 提示词包按固定顺序装配：
`mastery_tutor`、`mastery_session_mode`、`runtime_context`、`runtime_policy`、
`mastery_loop`、`mastery_playbook`、`tool_call_policy`、`tools`。
每块 SHALL 渲染为 `## <name>` 加正文；块之间 MUST 用 `\n\n---\n\n` 连接；
正文为空的块 MUST 整块省略。

#### Scenario: Blocks appear in the tutor's order

- **GIVEN** 任意一次 mastery 回合
- **WHEN** 读取发给模型的系统消息
- **THEN** `mastery_tutor` MUST 出现在 `mastery_session_mode` 之前，
  且 `mastery_loop` MUST 出现在 `mastery_playbook` 之前
- **AND** 块与块之间 MUST 有 `---` 分隔行

#### Scenario: The current mode paragraph is injected

- **GIVEN** 会话模式为 `study`
- **WHEN** 装配系统提示词
- **THEN** `mastery_session_mode` 块 MUST 是提示词包中 `session.study` 的正文
- **AND** 同一段落 MUST 由 `mastery_mode` 的 `instructions` 原样返回

#### Scenario: Runtime date is resolved from the clock

- **GIVEN** 当天为 2026-11-10
- **WHEN** 装配系统提示词
- **THEN** `runtime_context` 块 MUST 含 `2026年11月10日`

#### Scenario: Language directive closes the prompt

- **GIVEN** 响应语言为 `zh`
- **WHEN** 渲染系统提示词
- **THEN** 末尾 MUST 含中文语言要求，并允许学习者要求改用其它语言

### Requirement: Prompt Pack Assets And Tool Manifest

提示词包 MUST 逐字来自 DeepTutor 的 `mastery_loop.yaml`（`general`、`runtime_policy`、
`loop.system`、`loop.user`、`session.*`、`playbook`、`loop.finish_exhausted`、
`loop.settle_exhausted`）。`tools` 块 MUST 由工具 hint 资产渲染；没有 hint 的工具
MUST NOT 出现在清单里，清单为空时 MUST 写 `- 无`。

#### Scenario: Key protocol sentences survive

- **GIVEN** 提示词包已加载
- **WHEN** 读取 `loop.system` 与 `playbook`
- **THEN** 前者 MUST 含冒号规则（以冒号结尾就必须调用 `mastery_quiz`），
  后者 MUST 含「那就是一次模式请求」
- **AND** `runtime_policy` MUST 含 `<think>` 兜底写法

#### Scenario: Tool manifest lists hinted tools only

- **GIVEN** 可挂载工具含 `rag`、`mastery_quiz`
- **WHEN** 渲染 `tools` 块
- **THEN** 清单 MUST 含 `rag` 的短描述与参数格式
- **AND** MUST NOT 出现 `mastery_quiz` 条目
- **AND** 只传无 hint 的工具时 MUST 渲染 `- 无`
