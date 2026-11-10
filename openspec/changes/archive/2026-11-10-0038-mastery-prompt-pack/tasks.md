# Tasks

## 1. 资源

- [x] 1.1 复制 `mastery_loop.yaml` / `agentic_chat.yaml`（zh）到 `src/main/resources/prompts/zh/`。
- [x] 1.2 复制 `hints/zh/{rag,read_source}.yaml` 到 `prompts/zh/hints/`。

## 2. 装配

- [x] 2.1 新增 `MasteryPrompts`：YAML 解析、模式段落、运行时日期、工具调用上限、工具清单、语言指令。
- [x] 2.2 新增 `MasteryPromptAssembler`：固定块顺序、`## 块名` + `---` 渲染、空块省略。
- [x] 2.3 `TurnRunner` 装配系统提示词并传给 `AgentLoop`；语言取 `general.response_language`。
- [x] 2.4 `mastery_mode.instructions` 改用提示词包的 `session.<mode>`。

## 3. 验证

- [x] 3.1 新增 `MasteryPromptsTest`、`MasteryPromptAssemblerTest`；`TurnRunnerTest` 增加系统提示词断言。
- [x] 3.2 `.\scripts\verify.ps1 -Change 0038-mastery-prompt-pack` 通过，前端 `typecheck` / `build` 通过。
- [x] 3.3 回填 `docs/verify/0038-mastery-prompt-pack.md`，追加 `docs/regression-log.md` 与 `docs/traceability.md`。

## Workflow follow-up

- [x] 人类审查证据后归档。
