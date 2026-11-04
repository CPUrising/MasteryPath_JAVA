package com.masterypath.service;

import com.masterypath.domain.model.TopicSource;
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
 * 来源附加：整体替换 vs 只替换知识库。
 *
 * <p>核心区别：{@code setKnowledgeBaseSources} MUST NOT 清掉非 KB 来源，
 * 否则 UI 增删知识库会连带删掉用户已附加的文件。
 */
class SourceAttachmentTest {

    @TempDir
    Path tempDir;

    private LearningStore store;
    private LearningService service;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
        service = new LearningService(store);
        service.createPath("p", "learn X", "X");
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    private static TopicSource source(String id, String kind) {
        TopicSource source = new TopicSource();
        source.setId(id);
        source.setKind(kind);
        source.setLabel(id + ".md");
        return source;
    }

    @Test
    @DisplayName("attachSources 整体替换并发出 sources.attached")
    void attachReplacesWholeList() {
        List<Map<String, Object>> attached = service.attachSources("p",
                List.of(source("a", "file"), source("b", "file")));

        assertThat(attached).hasSize(2);
        assertThat(store.load("p").getSources()).hasSize(2);
        assertThat(store.eventsSince("p", 0))
                .extracting(event -> event.get("event_type"))
                .contains("sources.attached");
    }

    @Test
    @DisplayName("attachSources 第二次调用覆盖第一次（不是追加）")
    void attachOverwritesPrevious() {
        service.attachSources("p", List.of(source("a", "file")));
        service.attachSources("p", List.of(source("c", "book")));

        assertThat(store.load("p").getSources())
                .extracting(TopicSource::getId)
                .containsExactly("c");
    }

    @Test
    @DisplayName("setKnowledgeBaseSources 保留非 KB 来源并排在前面")
    void knowledgeBaseSourcesPreserveOtherKinds() {
        service.attachSources("p", List.of(source("file1", "file")));

        List<Map<String, Object>> merged = service.setKnowledgeBaseSources("p",
                List.of(source("kb:k1", "knowledge_base"), source("kb:k2", "knowledge_base")));

        assertThat(merged).hasSize(3);
        assertThat(store.load("p").getSources())
                .extracting(TopicSource::getId)
                .containsExactly("file1", "kb:k1", "kb:k2");
    }

    @Test
    @DisplayName("setKnowledgeBaseSources 用空列表只清空 KB，不动其他来源")
    void clearingKnowledgeBasesKeepsFiles() {
        service.attachSources("p", List.of(source("file1", "file")));
        service.setKnowledgeBaseSources("p", List.of(source("kb:k1", "knowledge_base")));

        List<Map<String, Object>> merged = service.setKnowledgeBaseSources("p", List.of());

        assertThat(merged).hasSize(1);
        assertThat(store.load("p").getSources())
                .extracting(TopicSource::getId)
                .containsExactly("file1");
    }

    @Test
    @DisplayName("setKnowledgeBaseSources 的 count 只计新增 KB 数量")
    void knowledgeBaseEventCountsOnlyIncoming() {
        service.attachSources("p", List.of(source("file1", "file")));

        service.setKnowledgeBaseSources("p", List.of(source("kb:k1", "knowledge_base")));

        Map<String, Object> event = store.eventsSince("p", 0).stream()
                .filter(item -> "sources.knowledge_bases".equals(item.get("event_type")))
                .findFirst()
                .orElseThrow();
        assertThat(event.get("payload")).asString().contains("\"count\":1");
    }

    @Test
    @DisplayName("序列化字段名与参考实现一致（snake_case）")
    void serializedSourceUsesSnakeCase() {
        TopicSource source = source("a", "file");
        source.setSourceId("src-1");

        List<Map<String, Object>> dumped = service.attachSources("p", List.of(source));

        assertThat(dumped.get(0)).containsKeys(
                "id", "kind", "source_id", "label", "excerpt", "position", "available", "metadata");
        assertThat(dumped.get(0)).containsEntry("source_id", "src-1");
    }

    @Test
    @DisplayName("null 来源列表按空列表处理")
    void nullSourcesTreatedAsEmpty() {
        service.attachSources("p", List.of(source("a", "file")));

        List<Map<String, Object>> attached = service.attachSources("p", null);

        assertThat(attached).isEmpty();
        assertThat(store.load("p").getSources()).isEmpty();
    }

    @Test
    @DisplayName("attachSources 可对不存在的路径创建（createIfMissing）")
    void attachCreatesMissingPath() {
        service.attachSources("fresh", List.of(source("a", "file")));

        assertThat(store.load("fresh")).isNotNull();
        assertThat(store.load("fresh").getSources()).hasSize(1);
    }
}
