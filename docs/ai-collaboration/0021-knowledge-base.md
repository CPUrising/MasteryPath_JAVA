# AI 协作留痕：0021-knowledge-base

## 1. prompt 范围

- 授权修改的文件：
  - `src/main/java/com/masterypath/knowledge/FolderScanner.java`（新增）
  - `src/main/java/com/masterypath/knowledge/VectorIndexer.java`（新增）
  - `src/main/java/com/masterypath/knowledge/HttpEmbedder.java`（新增）
  - `src/main/java/com/masterypath/storage/KnowledgeStore.java`（新增）
  - `src/main/java/com/masterypath/service/KnowledgeService.java`（新增）
  - `src/main/java/com/masterypath/adapter/web/KnowledgeController.java`（新增）
  - `src/main/java/com/masterypath/adapter/web/LearningController.java`（+3 端点，构造多一个参数）
  - `src/main/java/com/masterypath/adapter/web/WebConfig.java`（知识库 bean + 接线）
  - `src/main/java/com/masterypath/adapter/turn/TurnRunner.java`（`sourceIndex` 知识库分支）
  - `src/main/java/com/masterypath/service/ChatService.java`（`knowledgeContext` 委托）
  - `src/test/java/com/masterypath/{knowledge,storage,service,adapter/web}/*Test.java`（新增 5 个）
  - `src/test/java/com/masterypath/adapter/web/ApiTestSupport.java`（+知识库夹具）
  - `scripts/verify.ps1`（`-Scope api` 补 `*KnowledgeServiceTest`）
  - `openspec/changes/0021-knowledge-base/**`、`docs/**`
- 明确不做的事：提供方探测与 Task Model（`0022`）、评测 CLI（`0023`）、验收（`0024`）。
- 出处：用户指令原文「执行」；`docs/migration-plan.md` §5 第 21 行。

## 2. AI 产出

- 扫描：`FolderScanner`（`scanFolder` / `readText` / `walk` / `SUPPORTED_EXTENSIONS` / `SKIP_DIRS`）
- 存储：`KnowledgeStore`（`kb.json`、`raw/`、`index/`、`_SAFE_ID` / `_BAD_NAME`、`safeJoin`、链接文件夹、`collectDocuments`）
- 索引：`VectorIndexer`（字符窗口分块 + 批量嵌入 + `chunks.json` + 余弦 top-k）
- 嵌入：`HttpEmbedder`（OpenAI `/embeddings` 与 Ollama `/api/embed`，错误不泄漏响应体）
- 服务：`KnowledgeService`（嵌入解析、分块校验、后台索引 + 进度表、检索三重短路、参考块）
- 传输：`KnowledgeController`（12 端点）+ `LearningController` 的 sources / knowledge-bases 三端点
- 新增测试：`FolderScannerTest`（8）、`KnowledgeStoreTest`（20）、`VectorIndexerTest`（9）、
  `KnowledgeServiceTest`（17）、`KnowledgeApiTest`（19）

## 3. 人工修正点

- AI 自我修正 3 轮：
  1. **`storage/KnowledgeStore.java` 的包声明写成 `knowledge`**（文件在 `storage/` 下）
     → 与 `knowledge` 包同名类冲突，编译报「找不到符号 KnowledgeStore」。改包声明并补
     `import com.masterypath.knowledge.FolderScanner`。**实现缺陷**。
  2. **`HttpRequest.Builder.headers(Map)` 签名不存在**（只有 `header(String, String)`）
     → 改为逐个 `header(...)`。**实现缺陷**。
  3. **两处测试基线写错**：
     - `invalidChunkConfigRejected` 断言 `smaller than chunk_size`，但 `overlap=512` 先被
       白名单拦下。参考实现的「overlap ≥ size」分支是**死代码**（size 最小 512、
       overlap 最大 200），改断言为白名单文案，并补一条「非字典」用例。
     - `setPathKnowledgeBases` 用 `KnowledgeApiTest` 的库去喂 `ApiTestSupport` 的路径端点，
       两者是不同的临时 workspace。改为在同一个夹具里建档。

## 4. 回归结果

- 用例数 / 失败数：本 change 新增 73 / 0；全量 805 / 0（732 → 805）。
- 证据链接：[`docs/verify/0021-knowledge-base.md`](../verify/0021-knowledge-base.md)
- 日志：`artifacts/verify-0021-knowledge-base-20261105-104241.log`（全量）、
  `artifacts/verify-0021-knowledge-base-20261105-104210.log`（API 专项 139 项）
- 参考实现对照：`pytest tests/test_knowledge.py tests/test_knowledge_chat.py -q` → **22 passed**

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| 是否引入 LlamaIndex 或 JVM 向量库 | **不引入，自实现** | JVM 无等价库；引入会带来新依赖与持久化格式，破坏「数据等价」；只保证可观察的检索契约 |
| 分块按 token 还是字符 | **字符** | 无 tiktoken；`indexed_config.tokenizer` 写 `char/cl100k_base` 标明差异；`needs_reindex` 只比 size/overlap |
| 知识库数据放文件还是 SQLite | **文件系统** | 参考实现就是 `kb.json` + 目录；改库会让旧数据无法互通 |
| `getSession` 式的「不存在」如何表达 | **抛 `UnknownKnowledgeBase`** | 对齐参考实现的 `KeyError`，REST 层据此映射 404 |
| `_safe_join` 的拒绝范围 | **空 / 绝对 / 盘符 / `..` / 非支持扩展名** | 逐字复刻，并额外校验最终路径在 `raw/` 之下 |
| `scanStats` 的 `skipped` 含义 | **只统计被忽略规则丢弃的** | 扩展名不支持的直接不计数（参考实现同样如此） |
| 嵌入不可用时建档是否失败 | **不失败** | 状态置 `rag_prerequisite_missing` + 写 `error`；这是参考实现的断言 |
| 重复 reindex 如何处理 | **返回同一 `run_id`** | 参考实现的进度表判定；`test_concurrent_reindex_runs_once` 锁定 |
| 检索失败是否抛错 | **返回空列表** | 检索失败不能拖垮聊天；只有「库不存在」才抛 |
| `search` 的 `top_k` | **`knowledge_context` 用 4，`rag` 工具用 5** | 两处参考实现本就不同 |
| 索引用线程池还是每库一线程 | **单线程池** | 仍能并发，同库去重逻辑一致；避免无界线程 |
| `POST /paths/{id}/sources` 的文件夹不存在映射 | **404**（不是 422） | 参考实现 `FileNotFoundError` / `NotADirectoryError` → 404，全项目少见 |
| `PUT /paths/{id}/knowledge-bases` 的来源 `available` | **`kb.status == "ready"`** | 参考实现原样 |
| `TurnRunner.sourceIndex` 的知识库分支 | **拼装 `collectDocuments` 全文，失败退回 `excerpt`** | 参考实现同款降级 |
