# AI 协作留痕：0024-acceptance

## 1. prompt 范围

- 授权修改的文件：
  - `src/main/java/com/masterypath/storage/LearningStore.java`（修 `listMessages` 形状）
  - `src/test/java/com/masterypath/storage/LearningStoreTest.java`（补形状断言）
  - `docs/verify/0024-acceptance.md`（新增）
  - `docs/ai-collaboration/0024-acceptance.md`（新增）
  - `docs/regression-log.md`、`docs/verification-debt.md`、`README.md`
  - `openspec/changes/0024-acceptance/**`
- 明确不做的事：不改前端代码（只设 `NEXT_PUBLIC_API_BASE`）、不提交参考实现的 `data/`（含明文 Key）。
- 出处：用户指令原文「两项确认现在做」（旧库双向读取 + 前端联调）。

## 2. AI 产出

- 旧库 → Java：复制 `../MasteryPath/data/learning.db` 到 `artifacts/acceptance/refdb/`，
  Java 8081 vs 参考实现 8001 做 44 次结构化 JSON 比对（路径 1 + 路径端点 30 + 会话 8 + 设置 5）
- Java → Python：Java 在 `artifacts/acceptance/javadb/` 建库 → 停服 → 参考实现读取
- 前端联调：Next.js 14.2.35 指向 8080，验证路径列表/详情、设置中心 + 测试连接、聊天回合、知识库
- 缺陷修复：`LearningStore.listMessages` 去掉 `session_id`、解析 `data`

## 3. 人工修正点

- AI 自我修正 1 轮：
  1. **对比脚本首次把 `/coverage` 的 404 当成异常抛出**（参考实现对无覆盖报告的路径也返回 404）。
     改为「把 HTTP 状态码也当作可比较结果」，404 vs 404 视为一致。**脚本缺陷，非实现缺陷**。
- 验收过程中发现的**真实缺陷 1 处**（`listMessages` 多键 + `data` 未解析），已修并补断言。
  这是本项目唯一一处由「真实数据对比」而非单测发现的偏差。

## 4. 回归结果

- 用例数 / 失败数：全量 864 / 0（无新增用例；`LearningStoreTest` 的断言被加强）
- 证据链接：[`docs/verify/0024-acceptance.md`](../verify/0024-acceptance.md)
- 日志：`artifacts/verify-0024-acceptance-20261105-145734.log`
- 参考实现对照：旧库双向读取逐字段一致；前端 4 条主链路全部通过

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| 旧库验证用原库还是副本 | **副本** | 直接打开会写入 `schema` 表与初始化 DDL；原库 SHA256 前后一致才证明只读 |
| 反向验证是否固化为 JUnit | **不固化** | 依赖外部 venv 会让 CI 不可用；改为一次性脚本 + 报告留证 |
| 前端是否允许改动 | **不改** | 一旦必须改前端才能跑通，就说明协议不等价，应记缺陷而不是迁就 |
| `/settings/providers` 空白是否算缺陷 | **不算** | 前端侧栏指向 `/settings/connections`；前者是 `[section]` 动态路由占位 |
| `POST .../turns` 的 `ERR_ABORTED` 是否算缺陷 | **不算** | Java 日志无异常；重新提交后正常完成并落库 |
| `message_count=4` | **符合预期** | 首条消息触发了两次提交（第一次前端中断但后端已完成落库） |
| `listMessages` 修复是否放宽断言 | **没有** | 改的是实现去对齐参考实现；测试断言反而更严（`containsOnlyKeys` + 对象相等） |
