package com.masterypath.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;
import com.masterypath.service.KnowledgeService;
import com.masterypath.storage.KnowledgeStore;
import com.masterypath.storage.SettingsStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 知识库 REST（{@code ../MasteryPath/tests/test_knowledge_chat.py} 的 KB 部分）。
 *
 * <p>逐端点断言 404 / 422 的分工：未知库 404，参数非法 422。
 */
@Tag("api")
class KnowledgeApiTest {

    private static final ObjectMapper MAPPER = JsonSupport.objectMapper();

    @TempDir
    Path tempDir;

    private KnowledgeStore store;
    private SettingsStore settings;
    private KnowledgeService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        store = new KnowledgeStore(tempDir);
        settings = new SettingsStore(tempDir);
        service = new KnowledgeService(store, settings,
                config -> texts -> deterministicVectors(texts));
        mvc = MockMvcBuilders.standaloneSetup(new KnowledgeController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(MAPPER))
                .build();
    }

    @AfterEach
    void tearDown() {
        // KnowledgeStore 无连接可关；临时目录由 JUnit 清理。
    }

    // -- CRUD -----------------------------------------------------------------

    @Test
    @DisplayName("无嵌入模型时创建返回 201 + rag_prerequisite_missing")
    void createWithoutEmbedding() throws Exception {
        MvcResult result = mvc.perform(post("/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Empty KB\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        Map<String, Object> body = json(result);
        assertThat(body).containsEntry("status", "rag_prerequisite_missing")
                .containsEntry("id", "Empty-KB");
        assertThat(jsonList(mvc.perform(get("/knowledge-bases")).andReturn()))
                .extracting(row -> row.get("name")).containsExactly("Empty KB");
    }

    @Test
    @DisplayName("空白名字 → 422")
    void createBlankNameIs422() throws Exception {
        assertThat(mvc.perform(post("/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("非法分块配置 → 422")
    void createInvalidChunkConfigIs422() throws Exception {
        assertThat(mvc.perform(post("/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad\",\"chunk_size\":900,\"chunk_overlap\":64}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("未知库 → 404")
    void unknownIs404() throws Exception {
        assertThat(mvc.perform(get("/knowledge-bases/ghost"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(get("/knowledge-bases/ghost/files"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(get("/knowledge-bases/ghost/progress"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(delete("/knowledge-bases/ghost"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("路径穿越形状的 id → 404")
    void traversalShapedIdIs404() throws Exception {
        assertThat(mvc.perform(get("/knowledge-bases/..%2F..%2Fdata"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("PATCH 改分块配置后 needs_reindex 为 true")
    void patchConfigMarksNeedsReindex() throws Exception {
        configureEmbedding();
        String kbId = createKb("Snap");
        writeFile(kbId, "a.md", "content ".repeat(60));
        service.reindex(kbId, false);

        Map<String, Object> patched = json(mvc.perform(patch("/knowledge-bases/" + kbId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chunk_size\":1024,\"chunk_overlap\":128}"))
                .andReturn());

        assertThat(patched).containsEntry("needs_reindex", true);
        assertThat(patched).containsEntry("index_config_source", "kb");
    }

    @Test
    @DisplayName("PATCH 非法分块配置 → 422")
    void patchInvalidConfigIs422() throws Exception {
        String kbId = createKb("Bad");

        assertThat(mvc.perform(patch("/knowledge-bases/" + kbId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chunk_size\":512,\"chunk_overlap\":512}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("DELETE 返回 204；再删 404")
    void deleteThen404() throws Exception {
        String kbId = createKb("Temp");

        assertThat(mvc.perform(delete("/knowledge-bases/" + kbId))
                .andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(mvc.perform(delete("/knowledge-bases/" + kbId))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    // -- 文件夹与索引 ---------------------------------------------------------

    @Test
    @DisplayName("附加文件夹后开始索引，progress 最终 ready")
    void addFolderStartsIndexing() throws Exception {
        configureEmbedding();
        String kbId = createKb("Physics");
        Path folder = tempDir.resolve("docs");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("gravity.md"), "Gravity draws objects toward a center.");

        Map<String, Object> result = json(mvc.perform(post("/knowledge-bases/" + kbId + "/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(Map.of("folder", folder.toString()))))
                .andReturn());

        assertThat(result).containsEntry("status", "indexing").containsEntry("total_files", 1);

        Map<String, Object> progress = waitReady(kbId);
        assertThat(progress).containsEntry("status", "ready").containsEntry("percent", 1.0);

        Map<String, Object> detail = json(
                mvc.perform(get("/knowledge-bases/" + kbId)).andReturn());
        assertThat(detail).containsEntry("doc_count", 1);
        assertThat((Integer) detail.get("chunk_count")).isGreaterThan(0);

        List<Map<String, Object>> files = jsonList(
                mvc.perform(get("/knowledge-bases/" + kbId + "/files")).andReturn());
        assertThat(files).extracting(row -> row.get("path")).containsExactly("gravity.md");
        assertThat(files.get(0)).containsEntry("linked", false);
    }

    @Test
    @DisplayName("缺 folder → 422；文件夹不存在 → 404")
    void folderErrors() throws Exception {
        String kbId = createKb("Errors");

        assertThat(mvc.perform(post("/knowledge-bases/" + kbId + "/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
        assertThat(mvc.perform(post("/knowledge-bases/" + kbId + "/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folder\":\"" + tempDir.resolve("nope").toString()
                                .replace("\\", "\\\\") + "\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("无文档的 reindex 返回 empty，percent 为 null")
    void reindexWithoutDocuments() throws Exception {
        configureEmbedding();
        String kbId = createKb("Blank");

        Map<String, Object> result = json(
                mvc.perform(post("/knowledge-bases/" + kbId + "/reindex")).andReturn());

        assertThat(result).containsEntry("status", "empty");
        assertThat(json(mvc.perform(get("/knowledge-bases/" + kbId + "/progress")).andReturn()))
                .containsEntry("status", "empty")
                .containsEntry("percent", null);
    }

    @Test
    @DisplayName("链接文件夹不复制文件")
    void linkFolder() throws Exception {
        configureEmbedding();
        String kbId = createKb("Linked");
        Path folder = tempDir.resolve("external");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("ext.md"), "external content");

        Map<String, Object> result = json(mvc.perform(post("/knowledge-bases/" + kbId + "/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                Map.of("folder", folder.toString(), "mode", "link"))))
                .andReturn());

        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) result.get("folder");
        assertThat(entry).containsEntry("file_count", 1);
        assertThat(store.rawDir(kbId).resolve("ext.md")).doesNotExist();
    }

    // -- 上传 -----------------------------------------------------------------

    @Test
    @DisplayName("上传索引并报告块数")
    void uploadIndexes() throws Exception {
        configureEmbedding();
        String kbId = createKb("Uploads");

        MockMultipartFile file = new MockMultipartFile(
                "files", "note.md", "text/markdown", "Binary search halves a sorted list.".getBytes());
        Map<String, Object> body = json(mvc.perform(multipart("/knowledge-bases/" + kbId + "/upload")
                        .file(file)
                        .param("paths", "[\"note.md\"]"))
                .andReturn());

        assertThat(body).containsEntry("status", "indexing").containsEntry("uploaded", 1);
        assertThat(waitReady(kbId)).containsEntry("status", "ready");
        assertThat(json(mvc.perform(get("/knowledge-bases/" + kbId)).andReturn()))
                .containsEntry("doc_count", 1);
    }

    @Test
    @DisplayName("上传路径穿越 → 422")
    void uploadTraversalIs422() throws Exception {
        configureEmbedding();
        String kbId = createKb("Safe");

        MockMultipartFile file = new MockMultipartFile(
                "files", "evil.md", "text/markdown", "x".getBytes());
        assertThat(mvc.perform(multipart("/knowledge-bases/" + kbId + "/upload")
                        .file(file)
                        .param("paths", "[\"../evil.md\"]"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("删除文件 → 204")
    void deleteFile() throws Exception {
        String kbId = createKb("Files");
        writeFile(kbId, "a.md", "x");

        assertThat(mvc.perform(delete("/knowledge-bases/" + kbId + "/files/a.md"))
                .andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(jsonList(mvc.perform(get("/knowledge-bases/" + kbId + "/files")).andReturn()))
                .isEmpty();
    }

    // -- 路径来源端点 ---------------------------------------------------------

    @Test
    @DisplayName("PUT /learning/paths/{id}/knowledge-bases 写入 kb 来源")
    void setPathKnowledgeBases() throws Exception {
        ApiTestSupport api = new ApiTestSupport();
        try {
            api.service.createPath("p", "g", "t");
            // 用同一个夹具的知识库存储建档，否则路径端点看不到另一个 workspace 的库。
            api.knowledgeStore.create("Physics", "import", Map.of(), "", Map.of());

            Map<String, Object> body = json(api.mvc.perform(
                            put("/learning/paths/p/knowledge-bases")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"kb_ids\":[\"Physics\"]}"))
                    .andReturn());

            assertThat(body).containsKey("knowledge_bases").containsKey("sources");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sources = (List<Map<String, Object>>) body.get("sources");
            assertThat(sources).hasSize(1);
            assertThat(sources.get(0)).containsEntry("kind", "knowledge_base")
                    .containsEntry("source_id", "Physics");
        } finally {
            api.close();
        }
    }

    @Test
    @DisplayName("PUT knowledge-bases 的 kb_ids 非数组 → 422；未知 kb → 404")
    void setPathKnowledgeBasesErrors() throws Exception {
        ApiTestSupport api = new ApiTestSupport();
        try {
            api.service.createPath("p", "g", "t");

            assertThat(api.mvc.perform(put("/learning/paths/p/knowledge-bases")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"kb_ids\":\"nope\"}"))
                    .andReturn().getResponse().getStatus()).isEqualTo(422);
            assertThat(api.mvc.perform(put("/learning/paths/p/knowledge-bases")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"kb_ids\":[\"ghost\"]}"))
                    .andReturn().getResponse().getStatus()).isEqualTo(404);
        } finally {
            api.close();
        }
    }

    @Test
    @DisplayName("POST /learning/paths/{id}/sources 扫描文件夹并附加")
    void attachFolderSources() throws Exception {
        ApiTestSupport api = new ApiTestSupport();
        try {
            api.service.createPath("p", "g", "t");
            Path folder = tempDir.resolve("materials");
            Files.createDirectories(folder.resolve("sub"));
            Files.writeString(folder.resolve("a.md"), "Alpha");
            Files.writeString(folder.resolve("b.txt"), "Beta");
            Files.writeString(folder.resolve("sub/c.md"), "Gamma");

            List<Map<String, Object>> attached = jsonList(api.mvc.perform(
                            post("/learning/paths/p/sources")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(MAPPER.writeValueAsString(
                                            Map.of("folder", folder.toString()))))
                    .andReturn());

            assertThat(attached).hasSize(3);
            assertThat(attached).extracting(row -> row.get("label"))
                    .containsExactly("a.md", "b.txt", "sub/c.md");

            List<Map<String, Object>> listed = jsonList(api.mvc.perform(
                    get("/learning/paths/p/sources")).andReturn());
            assertThat(listed).hasSize(3);
        } finally {
            api.close();
        }
    }

    @Test
    @DisplayName("sources 端点：缺 folder → 422；未知路径列来源 → 404")
    void sourcesEndpointErrors() throws Exception {
        ApiTestSupport api = new ApiTestSupport();
        try {
            api.service.createPath("p", "g", "t");

            assertThat(api.mvc.perform(post("/learning/paths/p/sources")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andReturn().getResponse().getStatus()).isEqualTo(422);
            assertThat(api.mvc.perform(get("/learning/paths/ghost/sources"))
                    .andReturn().getResponse().getStatus()).isEqualTo(404);
        } finally {
            api.close();
        }
    }

    // -- 辅助 -----------------------------------------------------------------

    private String createKb(String name) throws Exception {
        Map<String, Object> created = json(mvc.perform(post("/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andReturn());
        return String.valueOf(created.get("id"));
    }

    private void writeFile(String kbId, String name, String content) throws IOException {
        Path raw = store.rawDir(kbId);
        Files.createDirectories(raw);
        Files.writeString(raw.resolve(name), content);
    }

    private Map<String, Object> waitReady(String kbId) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> progress = json(
                    mvc.perform(get("/knowledge-bases/" + kbId + "/progress")).andReturn());
            if (!"indexing".equals(progress.get("status"))) {
                return progress;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("indexing did not finish in time");
    }

    private void configureEmbedding() {
        settings.writeSection("providers", List.of(Map.of(
                "id", "ollama",
                "name", "Ollama",
                "base_url", "http://127.0.0.1:11434/v1",
                "api_key", "",
                "protocol", "ollama")));
        settings.writeSection("embedding", Map.of(
                "models", List.of(Map.of(
                        "id", "e1", "provider_id", "ollama",
                        "model", "bge", "name", "bge", "dimension", 8)),
                "active_id", "e1"));
    }

    static List<List<Double>> deterministicVectors(List<String> texts) {
        List<List<Double>> out = new ArrayList<>();
        for (String text : texts) {
            double[] vector = new double[8];
            for (String word : text.toLowerCase().split("\\s+")) {
                if (!word.isEmpty()) {
                    vector[Math.floorMod(word.hashCode(), 8)] += 1.0;
                }
            }
            List<Double> values = new ArrayList<>();
            for (double value : vector) {
                values.add(value);
            }
            out.add(values);
        }
        return out;
    }

    private static Map<String, Object> json(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private static List<Map<String, Object>> jsonList(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), List.class);
    }
}
