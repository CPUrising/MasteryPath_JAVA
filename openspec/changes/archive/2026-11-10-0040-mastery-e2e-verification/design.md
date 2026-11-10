# Design: 真实作答与真实模型对话验证设施

## 1. 三层验证

| 层 | 文件 | 依赖 | 是否默认回归 |
| --- | --- | --- | --- |
| L1 判分矩阵 | `GradingTest` / `MasteryGradingTest` + `AnswerFixtures` | 无 | 是 |
| L2 REST 契约 | `MasteryGradeContractTest` | `ApiTestSupport`（MockMvc + 临时 SQLite） | 是 |
| L3 真实模型 | `MasteryJourneyE2ETest` | 真实 Tomcat/WS/SQLite + 真实模型 | 否（`e2e` 标签） |

## 2. 13 条作答 fixture

`src/test/resources/fixtures/real-answers-13.json`，每条含：

`id / question_type / prompt / options / expected_answer / required_keywords /
optional_keywords / learner_answer / practice / mastery / note`。

`practice` 是 `Grading.grade(...).result()` 的期望，`mastery` 是
`MasteryGrading.isCorrect(...)` 的期望。两条链路的差异（ungraded / 正文判分 / 数值容差 /
模糊阈值 / 开放题比例）由此固定。

`AnswerFixtures.load()` 用 `JsonSupport.objectMapper()` 读成 `Map` 后手工映射成 record，
避免依赖全局 snake_case 命名策略的反序列化细节。

## 3. REST 契约用例

`ApiTestSupport` 已注册 `StudyController / SessionController / LearningController`，
因此全部走真实控制器与真实 SQLite：

- 首次答对 `mastery_after=0.5`、`gate_cleared=false`；
- 三次答对 `mastery_after=1.0`、`gate_cleared=true`；
- 无法解析的选择题 `result=incorrect`（不是 `ungraded`）；
- 判分反馈写入会话消息 `data.grade`；
- `GET /mastery` 反映门槛。

## 4. 真实模型 E2E

### 4.1 开关

- `@Tag("e2e")` + `@EnabledIfEnvironmentVariable(named = "MASTERY_E2E", matches = "1")`。
- 生产 `TurnRunner`/`ClientBuilder` 原样使用；不替换任何 Bean。

### 4.2 配置隔离

- `target/test-workspace/e2e-*` 建独立目录；
- 从 `MASTERY_E2E_DATA_DIR`（默认 `data`）复制 `settings.json` 进去；
- 用 `masterypath.data-dir` 指向该目录，数据库因此全新；
- 预检 `ModelResolution.resolveLlmConfig`，未配置模型则跳过（不算失败）。

### 4.3 驱动

- 建路径 → `outline` 模式一条带画像的开场，让真实模型 `mastery_build`；
  3 轮无大纲则测试直接 `service.build` 兜底（transcript 标记 `fallback=build`）。
- `PUT .../mode` 切 `study`。
- 主循环按 `next.action` 选择学习者发言（出题 / 作答 / 解释 / 复习）；
- 收到题目后从本地库读 `expected_answer` 作答（夹具手段，产品不暴露）；
- 每轮断言 `mastery_grade` 的 `tool_result` 先于 `text_delta`；
- 定性目标连续 3 轮未触发 `mastery_assess` 时用 REST `/assess` 兜底并标记。

### 4.4 断言与留痕

- 至少 4 次判分，`result ∈ {correct, incorrect}`，绝不出现 `ungraded`；
- 第 3 次正确后 `mastery_after=1.0`、`gate_cleared=true`；
- 会话消息成对持久化且 `data.grade` 条数一致；
- `learning_stages` 覆盖全部目标，至少一个 `independently_demonstrated`；
- `next.action=complete`，`reviews` 至少一条为已掌握目标播种；
- `target/e2e-artifacts/mastery-journey-*.jsonl` 记录每轮学习者发言、WS 帧、判分与下一步；
  结束时写 `.session.json`；`MASTERY_E2E_KEEP=1` 时把数据目录复制到 `artifacts/e2e/`。

## 5. 脚本

- `verify.ps1`：`all` 与 `unit` 排除 `e2e`；新增 `-Scope e2e`。
- `e2e-mastery.ps1`：预检设置、设置环境变量、运行 `-Dgroups=e2e`、打印 transcript 路径。

## 6. 风险

| 风险 | 处理 |
| --- | --- |
| 真实模型未按预期调用工具 | 主循环有限轮次 + 两类兜底；失败留 transcript |
| 费用 | 默认 18 轮上限、只跑一次；可用 `MASTERY_E2E_MAX_TURNS` 调小 |
| Key 泄露 | 只复制 settings.json，不打印；证据只引用数据目录路径 |
| 默认回归变慢或联网 | `e2e` 标签 + `verify.ps1` 默认排除 |
