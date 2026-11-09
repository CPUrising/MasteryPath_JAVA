# Tasks

## 1. Mock 服务

- [x] 1.1 新增 `scripts/mock-llm/mock-llm.mjs`：`POST /v1/chat/completions` 支持 SSE 与整包，`GET /v1/models` 返回固定模型；端口/延迟可用参数覆盖。
- [x] 1.2 实现剧本选择：tool 收尾 → 文本；`outline` → `mastery_build`；`study` → `mastery_quiz`；含「主题/list」→ `mastery_topics`；其余 → 含 Markdown 的文本。
- [x] 1.3 每次请求打印一行日志（模式 / 剧本 / 工具名），工具调用前校验 `tools` 里确实存在该工具。

## 2. 文档

- [x] 2.1 `README.md` 增加「不配 Key 体验 agent」小节：启动命令、`base_url` 填写方式、可观察到的场景。

## 3. 验证

- [x] 3.1 手工：`GET /v1/models` 与 `POST /v1/chat/completions` 直连，看到多帧 `data:` 与末尾 `[DONE]`。
- [x] 3.2 手工：后端指向 mock，完成普通聊天（SSE，`mastery_topics`）、建大纲（WS，`mastery_build`）与出题（WS，`question_card`）。
- [x] 3.3 `.\scripts\verify.ps1 -Change 0029-mock-llm-dev-server` 通过（868 / 0，生产代码零改动）。

## Workflow follow-up

- 追加 `docs/regression-log.md`。
- 新增 `docs/ai-collaboration/0029-mock-llm-dev-server.md` 留痕。
- 人类审查证据后执行 `openspec archive 0029-mock-llm-dev-server --skip-specs -y`。
