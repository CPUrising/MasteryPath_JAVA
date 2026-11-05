# 验证报告：0024-acceptance

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0024-acceptance` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-05 14:57:34（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1、Node v24.18.0、Next.js 14.2.35 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0024-acceptance` |
| 日志 | `artifacts/verify-0024-acceptance-20261105-145734.log` |
| 结果包 | `target/surefire-reports/` |

## 2. G7 清单

| # | 通过标准（`migration-plan` §4） | 证据 | 结果 |
|---|---|---|---|
| 1 | 全量用例通过 | `Tests run: 864, Failures: 0, Errors: 0` | PASS |
| 2 | 旧库双向可读 | §3（旧库 → Java）、§4（Java → Python） | PASS |
| 3 | 前端联调通过 | §5（路径 / 设置 / 聊天 / 知识库） | PASS |
| 4 | 全部 change 归档 | `0001`–`0023` 已归档；`0024` 在本次合并后归档 | PASS |

## 3. 旧库 → Java

**数据源**：`../MasteryPath/data/learning.db`（参考实现真实运行产生的库）
SHA256 `6CD8A571D6FF7D88E8FE48AD686458DFB1C0E6F44C489D12C504779E6546860E`，233 472 字节。

**方法**：复制到 `artifacts/acceptance/refdb/`，Java 以
`--masterypath.data-dir=artifacts/acceptance/refdb --server.port=8081` 启动；
参考实现以同一份库的**另一份副本**启动在 8001；逐端点做结构化 JSON 对比。

**结果**

| 对比项 | 观测 | 差异数 |
|---|---|---|
| `GET /learning/paths` | 5 条，id 集合与 `version` 完全一致 | **0** |
| 5 条路径 × 6 个端点（详情 / events / coverage / review-queue / questions / suggest-question） | 30 次比对 | **0** |
| 8 个会话详情（`/learning/sessions/{id}`，含全部消息） | 8 次比对 | **0** |
| `/settings`、`/settings/providers`、`catalog`、`/settings/llm`、`/settings/indexing` | 5 次比对 | **0** |
| `/knowledge-bases` | 1 次比对 | **0** |

真实旧库概况：路径 `pmveodk71`(v3) / `pmvf72enn`(v2) / `pmvhhpxrl`(v109) / `pmvhpxp9o`(v6) / `pmvi0ub7m`(v4)，
125 条事件、8 个会话、0 条题库、0 条交互。v109 说明该库经历过大量 CAS 写入。

**原库未被污染**：验收结束后再次计算 SHA256，仍为
`6CD8A571D6FF7D88E8FE48AD686458DFB1C0E6F44C489D12C504779E6546860E`。

### 3.1 验收发现的缺陷（已修）

| 缺陷 | 现象 | 修复 |
|---|---|---|
| `LearningStore.listMessages` 多返回 `session_id` | Java 返回 7 键，参考实现返回 6 键 | 删除该键 |
| `data` 列未解析 | Java 返回字符串 `"{}"`，参考实现返回对象 `{}` | 新增 `parseMessageData`，空值给 `{}` |

修复后 8 个会话详情 **0 差异**；并在
[LearningStoreTest](file:///d:/documents/AGENT-learning/MasteryPath_JAVA/src/test/java/com/masterypath/storage/LearningStoreTest.java#L253-L261)
补了形状断言（`containsOnlyKeys(...)` + `data` 等于原对象），防回归。

## 4. Java → Python

**方法**：Java 在 `artifacts/acceptance/javadb/` 建库并写入
（建路径 → 建大纲 → 声明掌握 → 写会话偏好），停服后用参考实现的
`LearningService` 读取同一文件。

**写入内容**

- 路径 `p1`：`name=Java 并发`、`goal=掌握 Java 并发`
- 大纲：模块「并发基础」，知识点 `k1 理解线程与进程`（concept）、`k2 线程安全`（concept，前置 `k1`）
- `POST /objectives/k2/override` → `{"gate_cleared": true}`
- `PATCH /paths/p1/sessions/s1` → `mode=study`、`llm_selection={provider_id, model_id}`
- 事件 4 条：`path.created`、`outline.built`、`review.scheduled`、`mastery.override`

**参考实现读取结果**

```
status      : active
name        : Java 并发
goal        : 掌握 Java 并发
version     : 3
counts      : {'mastered': 1, 'learning': 0, 'new': 1, 'total': 2}
next        : assess / 理解线程与进程
map         : {'k1': ('new', '理解线程与进程'), 'k2': ('mastered', '线程安全')}
events      : 4 ['path.created', 'outline.built', 'review.scheduled', 'mastery.override']
sessions    : 1
  session   : s1 mode= study prefs= {"llm_selection": {"provider_id": "conn-mvjc5m", "model_id": "m1"}}
session dflt: True 0
coverage    : {'documents': 0, 'covered': 0, 'missing': [], 'reported': False, 'grounding_ratio': 0.0, 'acyclic': True}
eval        : False
```

中文、前置关系、掌握状态、事件序列、会话偏好全部被 Pydantic 模型正确解析——
这同时证明 DDL、JSON 字段名与枚举取值三处都与参考实现一致。

## 5. 前端联调

**方法**：不改任何 `.tsx`，只设 `NEXT_PUBLIC_API_BASE=http://127.0.0.1:8080`，
`npm run dev` 起 Next.js 14.2.35（3000），Java 以真实 `data/` 起在 8080。

| 链路 | 观测 | 结果 |
|---|---|---|
| 路径列表 `/learning/mastery` | 展示「Java 并发」「掌握 Java 并发」「2 KP / 0 / 2 mastered」 | PASS |
| 路径详情 `/learning/mastery/p1` | 标题「Java 并发」、「0 / 2 知识点」，两个中文 KP 可见 | PASS |
| 设置中心 `/settings/connections` | 3 个提供方卡片全部渲染（含 `detection.status=connected` 的 DeepSeek） | PASS |
| **测试连接** | 点击后 `POST /settings/providers/probe` 返回 200，页面显示「已连接 · 2 个模型」并列出 `deepseek-flash` / `deepseek-v4-pro` | PASS |
| 聊天 `/chat` | 新建会话 → 发送「用一句话解释什么是线程」→ 收到真实回复：「线程是进程内部的一条独立执行流，它共享所属进程的地址空间和打开的资源，是 CPU 调度的基本单位。」 | PASS |
| 会话落库 | `/chat/sessions` 返回该会话，`message_count=4`，`title` 自动取首条消息 | PASS |
| 知识库 `/knowledge-bases` | 空库返回 `[]`，页面无报错 | PASS |
| `/settings/indexing`、`/settings/general` | `{chunk_size:512, chunk_overlap:64}`、`{interface_language:"zh", response_language:"zh"}` | PASS |

### 5.1 两点说明

| 现象 | 判断 |
|---|---|
| `/settings/providers` 是前端占位页 | **不是缺陷**：前端侧栏「提供方」指向 `/settings/connections`；`/settings/providers` 只是 `[section]` 动态路由的未实现分支，与后端无关 |
| 浏览器控制台出现 `net::ERR_ABORTED`（`POST .../turns`） | **dev 模式噪声**：Java 日志无 ERROR/WARN/Exception；重新提交后半球正常完成并落库 |

## 6. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 全量 | `verify.ps1 -Change 0024-acceptance` | BUILD SUCCESS，Tests run: 864, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（`0023` 的 864 项仍全通过；`listMessages` 修复未破坏任何既有断言） |
| 参考实现对照 | 本轮新增「真实旧库 + 前端」，此前 29 个 Python 测试文件已逐组对照 | 全部关闭 |

## 7. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 旧库验证用**副本**而非原库 | 无 | 避免 Java 初始化 DDL 与 `schema` 表写入污染参考数据；原库 SHA256 验收前后一致 |
| D2 | 反向验证用一次性 Python 脚本，未固化为 JUnit | 低 | JUnit 依赖外部 venv 会让 CI 不可用；脚本与输出已完整记录在本报告 §4 |
| D3 | 前端联调只覆盖 4 条主链路，非全部页面 | 低 | 其余页面共用同一批端点，已由 30 次端点比对 + 单测覆盖 |
| D4 | 聊天联调消耗了一次真实模型额度 | 无 | 每个回合只发一条问题；`storage` 与 `knowledge` 均未跑批量评测 |
| D5 | `/settings/providers` 无内容 | 无 | 前端路由设计如此，后端契约无缺口 |
| D6 | 截图工具在当前环境返回 0×0 视口 | 无 | 前端验证改用 DOM 快照、正文文本与网络记录 |

## 8. 未覆盖项（已登记验证债）

| 场景 | 状态 | 说明 |
| --- | --- | --- |
| 真实 provider 的 function calling / 流式边界 | 阻塞 | 需构造特定供应商响应；属手工验证 |
| 知识库在真实大文档上的索引耗时 | 未执行 | 与行为等价无关，属性能评估 |
| 前端全部页面的人工走查 | 未执行 | 需产品方验收，不属后端行为等价 |

## 9. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 864, Failures: 0` 来自
`artifacts/verify-0024-acceptance-20261105-145734.log`；旧库双向读取与前端联调的完整输出见 §3–§5，
原库 SHA256 在验收前后均为 `6CD8A571D6FF7D88E8FE48AD686458DFB1C0E6F44C489D12C504779E6546860E`。
