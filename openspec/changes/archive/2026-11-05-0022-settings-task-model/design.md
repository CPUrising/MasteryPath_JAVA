# Design: 设置探测与 Task Model

## Context

| 块 | 行号 | 内容 |
| --- | --- | --- |
| `settings/providers.py` | 1–485 | URL 校验、端点推导、鉴权头、模型分类、探测/取列表/测维度、`embed_texts`、目录 |
| `agents/task_model.py` | 1–76 | `suggest_question`（LLM 优先 + 5 条确定性回退）、`suggest_session_name` |
| `api/app.py` | 341–346 | `GET /learning/paths/{id}/suggest-question` |
| `api/app.py` | 408–467 | `_probe_fields` + 4 个探测端点 |

Java 侧 `0020` 已有 `SettingsStore`（落盘 + 归一），`0021` 已有 `HttpEmbedder`（同样的端点推导与鉴权头）。

## Goals / Non-Goals

**Goals**

1. 探测状态词表逐字一致：`connected` / `invalid_url` / `auth_error` / `unavailable` /
   `rate_limited` / `http_error` / `timeout` / `unreachable`。
2. 模型分类只靠关键词：embedding 提示词 7 个、排除词 10 个。
3. `suggest-question` 永不失败：LLM 报错或无模型时返回确定性草稿。
4. 探测**永不写入配置**，**永不回显凭据**。

**Non-Goals**

1. 不做 `POST /paths/{id}/eval` 的实现（属 `0023`）。
2. 不做 SSE / 前端联调。
3. 不引入 `httpx`；用 JDK `HttpClient`。

## Decisions

1. **`ProviderProbe` 是纯静态工具类，`HttpClient` 可注入**
   - 参考实现每个函数都接受可选 `client`，测试用 `MockTransport` 驱动。
   - Java 侧同样：`probeProvider(..., HttpClient client)`，`null` 时内部建一个。
   - 测试用 JDK `HttpServer` 起真实本地端点（`0015` 已用此法），不 mock 传输层。

2. **端点推导复刻 `_split_base_url` + `_discovery_request`**
   - 先剥掉 7 个推理后缀（`/embeddings`、`/chat/completions` 等）。
   - `ollama`：去掉尾部 `/v1` 后加 `/api/tags`；其余加 `/models`。
   - 探测与 `detect-dimension` 的 `ollama` 路径分别是 `/api/tags` 与 `/api/embed`。

3. **鉴权头三态**
   - `anthropic` → `x-api-key` + `anthropic-version: 2023-06-01`。
   - `ollama` → 不带（本地服务）。
   - 其余 → `Authorization: Bearer <key>`；key 为空则完全不带头。

4. **`validBaseUrl` 拒绝凭据与非法端口**
   - 必须 `http`/`https`、有 host、**无** userInfo。
   - `URI.getPort()` 在端口非法时抛 `IllegalArgumentException`，捕获后返回 `false`。

5. **`classifyModel` 的排除优先于嵌入**
   - 先查 10 个排除词（`whisper` / `tts` / `dall-e` / `image` / `video` / `rerank` /
     `moderation` / `audio` / `speech` / `transcribe`），命中返回**空串**（丢弃）。
   - 再查 7 个嵌入提示词（`embed` / `bge-` / `gte-` / `e5-` / `voyage` / `nomic` /
     `text-similarity`），命中返回 `embedding`；否则 `llm`。

6. **`_classify_items` 去重且保持首次出现顺序**
   - 同一 id 只保留一条；`services` 恒为单元素列表。

7. **`probe_provider` 的 anthropic 短路**
   - `anthropic` 直接返回 `{status: unavailable, models: [], warning: "manual_entry"}`，
     **不发请求**——Anthropic 没有模型列表端点。

8. **`service` 过滤**
   - `task` 视同 `llm`；`llm` / `embedding` 时只留含该 service 的模型。
   - 过滤后为空 → 附加 `warning: "no_models"`（状态仍是 `connected`）。

9. **`detect_dimension` 是唯一会「花一点钱」的探测**
   - 只在显式调用时执行；不支持的端点返回 `{status: unavailable, dimension: 0}`。
   - 维度解析支持 `{embeddings: [[...]]}` 与 `{data: [{embedding: [...]}]}`。

10. **`_probe_fields` 的「掩码回退」**
    - 编辑器提交的是屏幕上的值：`api_key` 为空**且**给了 `provider_id` → 用已保存的 key。
    - `protocol` / `base_url` 同理：提交值优先，其次已保存，最后 `openai` / 空串。

11. **`TaskModel.suggestQuestion` 的失败边界**
    - `clientFactory` 为 `null` → 直接回退。
    - 构造或调用抛任何 `RuntimeException` → 吞掉后回退。
    - 返回文本 strip 后为空 → 回退。
    - **永不抛异常**（`spec` 的硬要求）。

12. **5 条确定性回退逐字复刻**
    - `answer_pending` → 「先作答上面那张卡片，再继续。」
    - `review` → 「复习一下：{name} 你还记得吗？请用自己的话说说。」
    - `complete` → 「这条路径的目标已全部掌握，要不要开始一个新主题？」
    - `assess` → 「请用你自己的话解释 {name}，并举一个例子。」
    - 其余 → 「关于 {name}，你最先想到的是什么？」
    - `name` 缺省为「这个知识点」。

13. **`suggest_session_name` 取前 40 字，空则「新学习会话」**
    - 无 LLM 调用；纯确定性。

14. **`GET /paths/{id}/suggest-question` 的 404**
    - 路径不存在 → 404 `"unknown path"`（与 `mastery` 端点一致）。
    - 返回 `{suggestion}`，是**草稿**，不自动发送。

15. **`TaskModel` 的客户端工厂来自 `ModelResolution`**
    - `WebConfig` 传 `() -> ModelResolution.buildClient(resolveLlmConfig(null))`。
    - 无配置时该 lambda 抛 `LlmError`，被 `TaskModel` 吞掉 → 回退。这正是
      `test_fallback_without_client` 的语义。

16. **`embed_texts` 复用 `HttpEmbedder`**
    - `0021` 已实现同样的端点推导、鉴权头、响应解析。
    - 本 change 只把 `ProviderProbe.embedTexts` 转发给 `HttpEmbedder`，避免重复实现。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 探测泄漏 API Key | 凭据进日志或响应 | 只进请求头；错误只报状态码；用例断言 `str(result)` 不含 key |
| `classifyModel` 顺序错 | `whisper` 被当成 llm | 排除词优先；用例覆盖 `whisper-1` / `dall-e-3` |
| `ollama` 路径推导错 | 探测 404 | 用例断言实际请求路径是 `/api/tags` |
| `_probe_fields` 未回退 key | 「测试连接」需重输凭据 | 用例断言提交空 key 时用已保存的 |
| `suggestQuestion` 抛异常 | 输入框草稿接口 500 | 全路径 `try/catch`；用例用会抛错的客户端 |
| 回退文案不匹配 | 前端/测试断言失败 | 5 条逐字复刻 |
| `detect_dimension` 误触发计费 | 用户被扣费 | 只在显式 POST 时执行；不支持的端点直接短路 |
| 与 `HttpEmbedder` 重复实现 | 两处端点推导漂移 | `embedTexts` 直接委托 |

## Migration Plan

1. `settings/ProviderProbe`（URL / 分类 / 探测 / 取列表 / 测维度 / 目录 / `embedTexts`）。
2. `agents/TaskModel`（建议问题 + 会话名）。
3. `adapter/web/SettingsProbeController`（catalog + 3 个探测端点）。
4. `LearningController` 补 `suggest-question`；`WebConfig` 接线 `TaskModel`。
5. 测试：`ProviderProbeTest` / `TaskModelTest` / `SettingsProbeApiTest`。
6. `verify.ps1 -Scope api` → 全量 → `pytest tests/test_settings_probe.py tests/test_task_model.py -q` 对照 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/settings/providers.py` 与
  `agents/task_model.py` 为唯一依据。
- `POST /paths/{id}/eval` 的实现属 `0023`，本 change 不落地该端点。
