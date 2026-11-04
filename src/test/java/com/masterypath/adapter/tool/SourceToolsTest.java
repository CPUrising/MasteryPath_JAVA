package com.masterypath.adapter.tool;

import com.masterypath.domain.mode.SessionMode;
import com.masterypath.service.LearningService;
import com.masterypath.storage.LearningStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 来源工具与条件挂载（{@code ADR-023}）。
 *
 * <p>对照标准：{@code ../MasteryPath/tests/test_source_tools.py}。
 */
class SourceToolsTest {

    @TempDir
    Path tempDir;

    private LearningStore store;
    private LearningService service;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
        service = new LearningService(store);
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    @DisplayName("rag 返回命中，未知知识库被拒")
    void ragReturnsHitsAndRejectsUnknownKb() {
        ToolContext ctx = ctx();
        ctx.setKbIds(List.of("kb1"));
        ctx.setKnowledgeService(new FakeKnowledge());

        ToolResult ok = MasteryTools.all().get("rag").execute(Map.of("query", "mean"), ctx);
        assertThat(ok.ok()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hits = (List<Map<String, Object>>) ok.metadata().get("hits");
        assertThat(hits.get(0)).containsEntry("text", "kb1:mean");

        ToolResult bad = MasteryTools.all().get("rag")
                .execute(Map.of("query", "mean", "kb_id", "other"), ctx);
        assertThat(bad.ok()).isFalse();
        assertThat(bad.content()).contains("is not attached");
    }

    @Test
    @DisplayName("无知识库时 rag 被拒")
    void ragWithoutKbRefused() {
        ToolContext ctx = ctx();

        ToolResult result = MasteryTools.all().get("rag").execute(Map.of("query", "mean"), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content()).isEqualTo("No knowledge base is attached to this path.");
    }

    @Test
    @DisplayName("空 query 被拒")
    void ragRequiresQuery() {
        ToolContext ctx = ctx();
        ctx.setKbIds(List.of("kb1"));
        ctx.setKnowledgeService(new FakeKnowledge());

        ToolResult result = MasteryTools.all().get("rag").execute(Map.of("query", "  "), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content()).isEqualTo("rag needs a query");
    }

    @Test
    @DisplayName("多个知识库未指定 kb_id 时被拒")
    void ragRequiresKbIdWhenSeveral() {
        ToolContext ctx = ctx();
        ctx.setKbIds(List.of("kb1", "kb2"));
        ctx.setKnowledgeService(new FakeKnowledge());

        ToolResult result = MasteryTools.all().get("rag").execute(Map.of("query", "mean"), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content())
                .isEqualTo("rag needs kb_id when several knowledge bases are attached: kb1, kb2");
    }

    @Test
    @DisplayName("检索异常降级为 ok=false，不杀死回合")
    void ragDegradesOnRetrievalFailure() {
        ToolContext ctx = ctx();
        ctx.setKbIds(List.of("kb1"));
        ctx.setKnowledgeService(new BrokenKnowledge());

        ToolResult result = MasteryTools.all().get("rag").execute(Map.of("query", "mean"), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content()).startsWith("retrieval failed: ");
    }

    @Test
    @DisplayName("read_source 返回全文，未知来源被拒")
    void readSourceReturnsText() {
        ToolContext ctx = ctx();
        ctx.setSourceIndex(Map.of("src:a", "full source text"));

        ToolResult ok = MasteryTools.all().get("read_source")
                .execute(Map.of("source_id", "src:a"), ctx);
        assertThat(ok.ok()).isTrue();
        assertThat(ok.content()).isEqualTo("full source text");
        assertThat(ok.metadata()).containsEntry("char_count", 16);

        ToolResult bad = MasteryTools.all().get("read_source")
                .execute(Map.of("source_id", "src:missing"), ctx);
        assertThat(bad.ok()).isFalse();
        assertThat(bad.content()).contains("Unknown source_id 'src:missing'");
    }

    @Test
    @DisplayName("只有一个来源时可省略 source_id")
    void readSourceDefaultsToSingleSource() {
        ToolContext ctx = ctx();
        ctx.setSourceIndex(Map.of("src:a", "text"));

        ToolResult result = MasteryTools.all().get("read_source").execute(Map.of(), ctx);

        assertThat(result.ok()).isTrue();
        assertThat(result.metadata()).containsEntry("source_id", "src:a");
    }

    @Test
    @DisplayName("多个来源未指定 source_id 时被拒")
    void readSourceRequiresIdWhenSeveral() {
        ToolContext ctx = ctx();
        ctx.setSourceIndex(Map.of("src:b", "b", "src:a", "a"));

        ToolResult result = MasteryTools.all().get("read_source").execute(Map.of(), ctx);

        assertThat(result.ok()).isFalse();
        assertThat(result.content())
                .isEqualTo("read_source needs source_id when several sources are attached: src:a, src:b");
    }

    @Test
    @DisplayName("超长来源截断到 20000 字符")
    void readSourceTruncates() {
        ToolContext ctx = ctx();
        ctx.setSourceIndex(Map.of("src:a", "x".repeat(25_000)));

        ToolResult result = MasteryTools.all().get("read_source")
                .execute(Map.of("source_id", "src:a"), ctx);

        assertThat(result.ok()).isTrue();
        assertThat(result.content()).hasSize(SourceTools.READ_SOURCE_LIMIT);
        assertThat(result.metadata()).containsEntry("char_count", SourceTools.READ_SOURCE_LIMIT);
    }

    @Test
    @DisplayName("条件挂载：有输入才挂载")
    void mountedOnlyWhenInputsExist() {
        ToolContext full = ctx();
        full.setKbIds(List.of("kb1"));
        full.setSourceIndex(Map.of("src:a", "text"));
        assertThat(MasteryTools.mountedTools(full)).containsKeys("rag", "read_source");

        ToolContext bare = ctx();
        Map<String, Tool> mounted = MasteryTools.mountedTools(bare);
        assertThat(mounted).doesNotContainKeys("rag", "read_source");
    }

    // -- 辅助 -----------------------------------------------------------------

    private ToolContext ctx() {
        ToolContext ctx = new ToolContext(service, "p");
        ctx.setSessionMode(SessionMode.STUDY);
        return ctx;
    }

    /** 假知识库：返回固定命中。 */
    private static final class FakeKnowledge {
        @SuppressWarnings("unused")
        public List<Map<String, Object>> search(String kbId, String query, int topK) {
            return List.of(Map.of("source", "notes.md", "text", kbId + ":" + query));
        }
    }

    /** 抛异常的知识库。 */
    private static final class BrokenKnowledge {
        @SuppressWarnings("unused")
        public List<Map<String, Object>> search(String kbId, String query, int topK) {
            throw new IllegalStateException("index unavailable");
        }
    }
}
