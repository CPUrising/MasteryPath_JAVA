# 验证报告：0026-chat-stream-withdraw

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0026-chat-stream-withdraw` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-09 15:43:36（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.x（wrapper）、Spring Boot 3.5.16、Node 24.18.0、Next.js 14.2.35 |
| 运行环境 | 本地（非 CI） |
| 命令 | `.\scripts\verify.ps1 -Change 0026-chat-stream-withdraw` |
| 日志 | `artifacts/verify-0026-chat-stream-withdraw-20261109-154233.log`（全量） |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

### 2.1 Chat Turn Streaming

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| Incremental delta delivery | `OpenAiCompatibleClientTest#streamChatIsLazy` | PASS | 上游在写出第一片后阻塞 5s；消费方仍在 2s 内取到第一片，证明未攒批 |
| Turn persists transcript and ends | `ChatApiTest#streamPersistsMessages` | PASS | 落库 `[user, assistant]`，正文与标题正确，末帧 `turn_end` |
| Partial text survives upstream failure | `ChatServiceTest#keepsPartialTextOnError` | PASS | 已产出正文保留并发出 `error` |

### 2.2 Message Withdrawal

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| Withdraw truncates tail | `ChatApiTest#withdrawTruncatesTail`、`ChatStoreTest#deleteMessagesFromTruncatesTail` | PASS | 返回 204，详情消息列表为空 |
| Withdraw keeps earlier history | `ChatStoreTest#deleteMessagesFromKeepsEarlier` | PASS | 撤回第三条后仅保留前两条（`q1` / `a1`） |
| Unknown session | `ChatApiTest#withdrawTruncatesTail` | PASS | `/chat/sessions/ghost/messages/{id}` 返回 404 |
| Repeated withdrawal is idempotent | `ChatApiTest#withdrawTruncatesTail` | PASS | 同一条消息重复撤回仍返回 204 |

### 2.3 前端 UX（范围例外，位于 `../MasteryPath/web`）

`verify.ps1` 只覆盖 Java 仓库，以下证据由人工命令与浏览器实测产出，如实记录：

| 项 | 命令 / 观测 | 结果 |
|---|---|---|
| 类型检查 | `npm run typecheck` | PASS（无输出即通过） |
| 生产构建 | `npm run build` | PASS（8 个路由全部生成，`/chat`、`/chat/[sessionId]` 均成功） |
| 浅色主题 | 打开 `http://localhost:3000/chat`，读取计算样式 | PASS：`body` 背景 `rgb(255,255,255)`，无 `dark` 类；与 `/learning/mastery` 一致 |
| 输入区工具栏 | 浏览器可访问性快照 | PASS：附件、知识库、能力标签、浅色模型选择器、发送按钮均存在 |

前端改动涉及 `ChatWorkspace`、`ChatMessageList`、`MarkdownMessage`、`MessageComposer`、`chat-api`、i18n 与 `globals.css`；
新增依赖 `react-markdown@^9`、`remark-gfm@^4`（`package-lock.json` 已更新）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 全量单元测试 | `.\scripts\verify.ps1 -Change 0026-chat-stream-withdraw` | BUILD SUCCESS，Tests run: **868**, Failures: 0, Errors: 0, Skipped: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（`0025` 的 864 项全部仍通过） |
| 前端类型检查 | `npm run typecheck` | PASS |
| 前端构建 | `npm run build` | PASS |
| 参考实现对照 | `../MasteryPath` 的 `masterypath/agents/llm.py`、`chat/service.py` | 惰性生成器语义一致；撤回端点为新增能力，参考实现无对应用例 |

本 change 新增 Java 用例 **4 个**（`OpenAiCompatibleClientTest` 1 + `ChatStoreTest` 2 + `ChatApiTest` 1），全部 PASS；总数 864 → 868。

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 前端改动超出 `openspec/project.md` 的 Out of scope（「不改写前端」） | 一般 | 人类于 2026-11-09 明确要求并批准为例外；已在 `proposal.md` 的 Impact 记录，范围仅限聊天页 UX |
| D2 | 新增 `DELETE /chat/sessions/{sessionId}/messages/{messageId}`，参考实现无此端点 | 一般 | 同上，人类批准为新增行为；已写入 `chat-session` delta spec |
| D3 | 流式惰性化与 `streamChatEvents(...)` 的既有签名并存 | 无 | 新方法默认委托旧方法，旧调用方语义不变；回归 868 项通过 |
| D4 | 前端构建/主题证据不由 `verify.ps1` 产出 | 低 | 已在 §2.3 记录实际命令与浏览器观测，不冒充自动化证据 |
| D5 | 撤回为物理删除，删除后不可恢复 | 低 | 与用户「撤回」语义一致；已在 design.md 的风险中记录 |

## 5. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 868, Failures: 0` 来自
`artifacts/verify-0026-chat-stream-withdraw-20261109-154233.log`。
前端 `typecheck` / `build` 与浏览器主题检查为本地手工执行，未计入 Java 用例总数。
归档前请人类复核 D1 / D2 的范围例外。
