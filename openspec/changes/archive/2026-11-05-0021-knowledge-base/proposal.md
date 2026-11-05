# Proposal: 知识库

## Why

`0020` 打通了普通聊天，但 `ChatService.knowledgeContext()` 恒返回空串、`TurnRunner` 的
`knowledgeService` 恒为 `null`——「参考材料」这条链路至今是空的。

参考实现的知识库能力分四块（合计约 1000 行）：

| 块 | 行号 | 内容 |
| --- | --- | --- |
| `knowledge/loader.py` | 106 | 文件夹扫描为来源、`read_text`、`{label: id}` 映射 |
| `knowledge/store.py` | 425 | `knowledge_bases/<id>/{kb.json,raw/,index/}` 元数据与文件 |
| `knowledge/service.py` | 441 | 嵌入解析、索引进度、检索、分块配置 |
| `api/knowledge_routes.py` | 159 | 12 个 REST 端点 |

没有这一层：`/knowledge-bases` 全部 404、`rag` / `read_source` 永远无内容、
`/learning/paths/{id}/sources` 与 `/knowledge-bases` 端点缺失。

## What Changes

- 新增 `knowledge/FolderScanner`：递归扫描、`read_text`（坏字节替换）、来源构造
- 新增 `storage/KnowledgeStore`：`kb.json` 元数据、`raw/` 文件、链接文件夹、上传安全路径
- 新增 `knowledge/VectorIndexer`：分块 + 嵌入 + 余弦检索（**自实现**，不用 LlamaIndex）
- 新增 `service/KnowledgeService`：CRUD、嵌入解析、异步索引与进度、检索、分块配置校验
- 新增 `adapter/web/KnowledgeController`：12 个 `/knowledge-bases` 端点
- 补齐 `LearningController`：`POST/GET /learning/paths/{id}/sources`、`PUT .../knowledge-bases`
- 接线：`TurnRunner` / `ChatService` 接上真实 `KnowledgeService` 与 `knowledgeContext`

## Capabilities

### New Capabilities

无新增能力；实现 `specs/knowledge-base` 的四条 Requirement（行为以参考实现 `knowledge/` 为唯一依据）。

### Modified Capabilities

无。`skip_specs: true`。

## Impact

- 影响文件：
  - `src/main/java/com/masterypath/knowledge/*`（新增 3 个类）
  - `src/main/java/com/masterypath/storage/KnowledgeStore.java`（新增）
  - `src/main/java/com/masterypath/service/KnowledgeService.java`（新增）
  - `src/main/java/com/masterypath/adapter/web/KnowledgeController.java`（新增）
  - `src/main/java/com/masterypath/adapter/web/{LearningController,WebConfig}.java`
  - `src/main/java/com/masterypath/adapter/turn/TurnRunner.java`、`service/ChatService.java`
  - `src/test/java/com/masterypath/**/Knowledge*Test.java`（新增）
  - `scripts/verify.ps1`（`-Scope api` 补 `*Knowledge*Test`）
- 影响测试：既有 732 个用例**不修改**。
- 明确不做（Out of scope）：
  - 提供方探测（`/settings/providers/probe`）与 Task Model → 属 `0022-settings-task-model`
  - 大纲评测 CLI → 属 `0023-outline-eval-cli`
  - 验收 → 属 `0024-acceptance`
