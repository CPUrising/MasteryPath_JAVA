package com.masterypath.adapter.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 来源工具：{@code rag} / {@code read_source}（{@code ADR-023}）。
 *
 * <p>两者都是<b>条件挂载</b>：没有知识库就不挂 {@code rag}，没有来源就不挂
 * {@code read_source}（见 {@link MasteryTools#mountedTools}）。
 *
 * <p>检索与读取都<b>不调模型</b>（{@code INV-32}）。
 */
public final class SourceTools {

    /** 单次读取的字符上限（参考实现 {@code [:20_000]}）。 */
    public static final int READ_SOURCE_LIMIT = 20_000;

    private SourceTools() {
    }

    // -- rag ------------------------------------------------------------------

    /** 检索知识库（{@code rag}）。 */
    public static final class Rag implements Tool {

        @Override
        public String name() {
            return "rag";
        }

        @Override
        public String description() {
            return "Retrieve relevant passages from one of the knowledge bases attached to "
                    + "this path. Use it when the source text matters; never invent a source "
                    + "that is not attached.";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            String query = ToolSupport.stripped(params.get("query"));
            if (query.isEmpty()) {
                return ToolResult.failure("rag needs a query");
            }
            if (ctx.knowledgeService() == null || ctx.kbIds().isEmpty()) {
                return ToolResult.failure("No knowledge base is attached to this path.");
            }
            String requested = ToolSupport.stripped(
                    params.get("kb_id") != null ? params.get("kb_id") : params.get("kb_name"));
            String kbId = requested;
            if (kbId.isEmpty()) {
                if (ctx.kbIds().size() != 1) {
                    return ToolResult.failure(
                            "rag needs kb_id when several knowledge bases are attached: "
                                    + String.join(", ", ctx.kbIds()));
                }
                kbId = ctx.kbIds().get(0);
            }
            if (!ctx.kbIds().contains(kbId)) {
                return ToolResult.failure("Knowledge base " + ToolSupport.repr(kbId)
                        + " is not attached. Attached: " + String.join(", ", ctx.kbIds()));
            }
            List<?> hits;
            try {
                hits = search(ctx.knowledgeService(), kbId, query);
            } catch (RuntimeException e) {
                // 检索失败不杀死回合。
                return ToolResult.failure("retrieval failed: " + e.getMessage());
            }
            Map<String, Object> payload = StatePayload.map(
                    "kb_id", kbId, "query", query, "hits", hits);
            return ToolResult.ok(StatePayload.jsonOf(payload), payload);
        }
    }

    // -- read_source ----------------------------------------------------------

    /** 读取来源全文（{@code read_source}）。 */
    public static final class ReadSource implements Tool {

        @Override
        public String name() {
            return "read_source";
        }

        @Override
        public String description() {
            return "Read the full text of one attached source by its id or label. Use only "
                    + "when the excerpt shown in mastery_status is not enough; do not invent "
                    + "source ids.";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            String sourceId = ToolSupport.stripped(params.get("source_id"));
            if (sourceId.isEmpty()) {
                if (ctx.sourceIndex().size() == 1) {
                    sourceId = ctx.sourceIndex().keySet().iterator().next();
                } else {
                    List<String> available = new ArrayList<>(ctx.sourceIndex().keySet());
                    java.util.Collections.sort(available);
                    return ToolResult.failure(
                            "read_source needs source_id when several sources are attached: "
                                    + String.join(", ", available));
                }
            }
            String text = ctx.sourceIndex().get(sourceId);
            if (text == null) {
                List<String> available = new ArrayList<>(ctx.sourceIndex().keySet());
                java.util.Collections.sort(available);
                return ToolResult.failure("Unknown source_id " + ToolSupport.repr(sourceId)
                        + ". Available: "
                        + (available.isEmpty() ? "(none)" : String.join(", ", available)));
            }
            String clipped = ToolSupport.truncate(text, READ_SOURCE_LIMIT);
            return ToolResult.ok(clipped, StatePayload.map(
                    "source_id", sourceId,
                    "char_count", clipped.length()));
        }
    }

    // -- 辅助 -----------------------------------------------------------------

    /**
     * 调用知识库检索。
     *
     * <p>知识库服务在 {@code 0020} 落地；本 change 只按「有 {@code search(kbId, query, topK)}
     * 方法」的鸭子类型调用，避免提前引入依赖。
     */
    private static List<?> search(Object knowledgeService, String kbId, String query) {
        try {
            var method = knowledgeService.getClass()
                    .getMethod("search", String.class, String.class, int.class);
            Object result = method.invoke(knowledgeService, kbId, query, 5);
            return result instanceof List<?> list ? list : List.of();
        } catch (NoSuchMethodException e) {
            // 退化为两参签名。
            try {
                var method = knowledgeService.getClass()
                        .getMethod("search", String.class, String.class);
                Object result = method.invoke(knowledgeService, kbId, query);
                return result instanceof List<?> list ? list : List.of();
            } catch (ReflectiveOperationException inner) {
                throw new IllegalStateException("knowledge service has no search method", inner);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("retrieval failed", e);
        }
    }

    /** 空 map（避免重复字面量）。 */
    static Map<String, Object> emptyMap() {
        return new LinkedHashMap<>();
    }
}
