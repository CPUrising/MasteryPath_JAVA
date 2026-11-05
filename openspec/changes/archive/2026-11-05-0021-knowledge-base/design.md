# Design: 知识库

## Context

参考实现 `knowledge/` 四块 + `api/knowledge_routes.py`：

| 块 | 行号 | 关键点 |
| --- | --- | --- |
| `loader.py` | 1–106 | `SUPPORTED_EXTENSIONS`、`_SKIP_DIRS`、`read_text(max_bytes)`、`scan_folder`、`build_source_map` |
| `store.py` | 1–425 | `_SAFE_ID` / `_BAD_NAME` 正则、`kb.json`、`raw/`、`linked_folders`、`_safe_join` |
| `indexer.py` | 1–222 | LlamaIndex 分块 + 向量持久化 + 余弦检索 |
| `service.py` | 1–441 | 嵌入解析、`normalize_chunk_config`、进度表、`reindex` 后台线程 |
| `knowledge_routes.py` | 1–159 | 12 个端点 |

Java 侧 `0018` 已有 `LearningController`（缺 sources 三个端点）、`0019`/`0020` 已把
`knowledgeService` 留成 `Object` 占位。本 change 落地真实实现并接线。

## Goals / Non-Goals

**Goals**

1. 文件夹扫描与来源构造逐字对齐（含隐藏文件与构建目录跳过、稳定排序）。
2. `kb.json` 元数据字段名与参考实现一致；目录布局 `knowledge_bases/<id>/{kb.json,raw/,index/}`。
3. 检索**不调 LLM**（`INV-32`）：只用本地余弦相似度。
4. 12 个 REST 端点与错误码（404 未知库 / 422 参数非法）。
5. `knowledge_context` 注入到 mastery 与 chat 两条回合链路。

**Non-Goals**

1. 不做提供方探测与 Task Model → `0022`。
2. 不引入 LlamaIndex / 任何向量库依赖 → 自实现余弦检索（见决策 3）。
3. 不做 BM25 混合检索（参考实现在 Python 3.14 上同样只有向量检索）。
4. 不做 PDF/DOCX 解析（参考实现只支持 `.txt` / `.md` / `.markdown`）。

## Decisions

1. **不引入 LlamaIndex，自实现分块 + 余弦检索**
   - 参考实现依赖 `llama_index`，但 Java 侧没有等价库；引入一个 JVM 向量库会带来
     新的依赖与持久化格式，反而破坏「数据等价」。
   - 本 change 的检索语义**只要求**：分块、按 `chunk_size` / `chunk_overlap` 切分、
     按余弦相似度取 top-k、结果含 `{text, source, score}`。这与 `indexer.search` 的
     返回形状一致。
   - 持久化用 `index/chunks.json`（`[{source, text, embedding}]`），是本实现自己的格式；
     参考实现的 LlamaIndex 目录不互通（`0024` 的验收只要求学习数据互通）。

2. **分块用字符窗口 + 重叠，不用 token 计数器**
   - 参考实现用 `SentenceSplitter(chunk_size=512, chunk_overlap=64)`（token 单位，
     `tiktoken/cl100k_base`）。
   - Java 侧无 tiktoken；用**字符窗口**近似：`size` 与 `overlap` 直接按字符解释。
   - 影响：同样配置下 chunk 数不同。这属于「实现细节不等价」，已在 `verify` 的偏差表登记。
   - `indexed_config.tokenizer` 写 `"char/cl100k_base"` 以标明差异，且 `needs_reindex`
     判定只比较 `chunk_size` / `chunk_overlap`（与参考实现一致，不看 tokenizer）。

3. **`KnowledgeStore` 用文件系统，不用 SQLite**
   - 参考实现就是 `kb.json` + 目录。改数据库会让 `0024` 的旧数据互通失效。

4. **`_SAFE_ID` / `_BAD_NAME` 逐字复刻**
   - `_SAFE_ID = ^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`：拒绝 `../` 等路径穿越。
   - `_BAD_NAME` 去掉 `\ / : * ? " < > |` 与控制字符。
   - `_safe_join` 拒绝绝对路径、盘符、`..`，并校验最终路径在 `raw/` 之下。

5. **`scan_folder` 跳过隐藏项与构建目录，且区分「跳过数」**
   - `_walk` 返回 `(kept, skipped)`；`skipped` 只统计**被忽略规则丢弃**的文件
     （隐藏名 / `_SKIP_DIRS`），不含「扩展名不支持」——后者直接不计数。

6. **`read_text` 用 UTF-8 + 替换坏字节**
   - 等价 Python 的 `decode("utf-8", errors="replace")`。
   - 默认 `max_bytes=200_000`（loader）；`collect_documents` 用 2_000_000。

7. **索引是后台线程 + 进度表**
   - 参考实现 `reindex(background=True)` 起 `threading.Thread`，进度存内存 `_progress`。
   - Java 侧同样用内存 `ConcurrentHashMap<String, Map<String,Object>>` + 单线程 `Executor`。
   - **同一 KB 已在索引时，重复请求返回同一个 `run_id`**（`test_concurrent_reindex_runs_once`）。

8. **`_percent` 的边界**
   - `total <= 0` → `null`；否则 `clamp(indexed / total, 0, 1)`。

9. **`progress` 无运行记录时按状态合成**
   - `status == "ready"` → `percent = 1.0`；否则 `null`。`total_files` 取 `doc_count`。

10. **`needs_reindex` 只在有 `indexed_config` 时才可能为真**
    - 参考实现：`bool(indexed) and not same`。旧库无快照 → 永远 `false`。

11. **`create` 在嵌入不可用时仍成功建档**
    - 状态置 `rag_prerequisite_missing`，`error` 存原因；这是
      `test_create_without_embedding_is_prerequisite_missing` 的断言。

12. **`search` 的三重短路**
    - 库不存在 → 抛 `UnknownKnowledgeBase`（404）。
    - `status != "ready"` → 返回空列表（不是错误）。
    - 嵌入解析失败或检索异常 → 返回空列表（检索失败不能拖垮聊天）。

13. **`knowledge_context` 逐字复刻**
    - `knowledgeService == null` 或 `query.strip()` 为空 → `""`。
    - 每个 kb：检索失败 → 跳过；命中为空 → 跳过；名称取不到 → 用 `kb_id`。
    - 拼装：`"Reference material:\n\n" + blocks.join("\n\n")`，每块 `"# {name}\n{joined}"`，
      每条命中 `"[{source or 'source'}]\n{text}"`。

14. **`TurnRunner` 的 `sourceIndex` 知识库分支接真实正文**
    - 参考实现：`knowledge_base` 来源调 `store.collect_documents(kb_id)`，
      拼成 `"[{label}]\n{body}"`；失败则退回 `excerpt`。
    - 文件来源读磁盘（失败退回 `excerpt`）——`0019` 已实现。

15. **`ChatService` 的 `knowledgeContext` 改为委托 `KnowledgeService`**
    - `0020` 的占位返回 `""`；本 change 替换为真实检索。

16. **`POST /learning/paths/{id}/sources` 的 404 与 422 分工**
    - 缺 `folder` → **422** `"folder required"`。
    - 文件夹不存在 / 不是目录 → **404**（`FileNotFoundError` / `NotADirectoryError` 映射）。
    - 这是全项目少见的「文件系统错误映射 404」，照抄。

17. **`PUT /learning/paths/{id}/knowledge-bases` 的 `kb_ids` 校验**
    - 非列表 → 422 `"kb_ids must be an array"`。
    - 任一 kb 不存在 → 404 `"unknown knowledge base: {id}"`。
    - 来源 `available` 取 `kb.status == "ready"`。

18. **上传端点用 `MultipartFile`**
    - 参考实现用 `UploadFile` + `paths` 表单字段（JSON 数组）。
    - `paths` 解析失败 → 空列表（退回文件名）。
    - 路径穿越 → 422（`KnowledgeError` 映射）。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 分块按字符而非 token | chunk 数与参考实现不同 | 登记为已知偏差；`needs_reindex` 判定只看 size/overlap，行为一致 |
| 索引格式不互通 | 参考实现的 `index/` 目录读不了 | `0024` 验收只要求学习数据（`learning.db`）互通；知识库可重建索引 |
| 后台线程无进度持久化 | 进程重启后进度丢失 | 参考实现同样只在内存；`progress` 无记录时按 `status` 合成 |
| `_safe_join` 校验遗漏 | 路径穿越写文件 | 用例覆盖 `../evil.md`、绝对路径、盘符三种 |
| `knowledge_context` 注入顺序错 | 模型看到错误上下文 | 用例断言 `Reference material` 在 system 消息里且含 `gravity.md` |
| 检索异常未吞 | 聊天整回合失败 | `search` 内部 `try/catch` 返回空列表；`knowledgeContext` 再兜一层 |
| 12 个端点错误码混用 | 前端拿错状态 | 用例逐端点断言 404 / 422 |

## Migration Plan

1. `knowledge/FolderScanner`（`scanFolder` / `readText` / `TopicSource` 构造）。
2. `storage/KnowledgeStore`（`kb.json` / `raw/` / 链接文件夹 / `_safe_join` / `collectDocuments`）。
3. `knowledge/VectorIndexer`（分块 + 嵌入 + 持久化 + 余弦检索）。
4. `service/KnowledgeService`（CRUD / 嵌入解析 / 分块校验 / 后台索引 / 进度 / 检索）。
5. `adapter/web/KnowledgeController`（12 端点）+ `LearningController` 三个 sources 端点。
6. 接线：`WebConfig`、`TurnRunner`、`ChatService`。
7. 测试：`FolderScannerTest` / `KnowledgeStoreTest` / `VectorIndexerTest` /
   `KnowledgeServiceTest` / `KnowledgeApiTest` / `KnowledgeContextTest`。
8. `verify.ps1 -Scope api` → 全量 → `pytest tests/test_knowledge.py tests/test_knowledge_chat.py -q` 对照 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/knowledge/` 为唯一依据。
- 分块粒度的偏差（字符 vs token）已在决策 2 与 Risks 中登记，不阻塞行为等价的其他维度。
