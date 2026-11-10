# Proposal: 真实作答与真实模型对话验证设施

## Why

0036–0039 把掌握路径的判分、回合协议、提示词、学习阶段逐项对齐了 DeepTutor，但验证仍以
离线脚本化客户端为主，缺少两样证据：

1. 真实会话里出现过的作答形态，是否在两条判分链路上都得到预期结果；
2. 真实模型、真实 HTTP/WebSocket 下，能否走完整条掌握之路。

本 change 只增加验证设施，不改生产行为：一份 13 条作答的 fixture、一组 REST 契约用例、
一条真实模型全链路 E2E，以及把 E2E 从默认回归中隔离出来的脚本改动。

## What Changes

- 新增 `fixtures/real-answers-13.json`：13 条脱敏改写的作答，分别标注练习题卷与掌握路径的期望。
- `GradingTest` / `MasteryGradingTest` 各增加一个用例，用同一份 fixture 固定两条链路的差异。
- 新增 `MasteryGradeContractTest`：`POST /grade`、`GET session`、`GET mastery` 的字段与落库。
- 新增 `MasteryJourneyE2ETest`：`@Tag("e2e")`，环境变量开启，使用真实模型与真实设置，
  经 `/ws` 与 REST 走完一条掌握之路，并把 WS 帧与会话内容落盘。
- `scripts/verify.ps1` 默认排除 `e2e`；新增 `scripts/e2e-mastery.ps1` 单独运行。
- 新增 `openspec/changes/0040-mastery-e2e-verification/**` 与验证证据。

## Impact

- 测试：`GradingTest`、`MasteryGradingTest`、`MasteryGradeContractTest`、`MasteryJourneyE2ETest`、
  `testsupport/AnswerFixtures`、`fixtures/real-answers-13.json`。
- 脚本：`scripts/verify.ps1`、`scripts/e2e-mastery.ps1`。
- 生产代码零改动。
- 默认回归零网络零费用；真实模型仅由 `e2e-mastery.ps1` 触发。
