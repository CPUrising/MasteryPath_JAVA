package com.masterypath.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;
import com.masterypath.service.KnowledgeService;
import com.masterypath.service.LearningService;
import com.masterypath.service.QuestionBankService;
import com.masterypath.service.QuizSetService;
import com.masterypath.storage.KnowledgeStore;
import com.masterypath.storage.LearningStore;
import com.masterypath.storage.SettingsStore;

import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST 测试夹具：每个用例一个临时 workspace + 独立 {@link MockMvc}。
 *
 * <p>用 {@code standaloneSetup} 而不是 {@code @SpringBootTest}：控制器只依赖
 * {@code LearningService} / {@code LearningStore}，手工装配即可，且天然隔离
 * （共享单例 store 会让用例互相污染）。
 *
 * <p>消息转换器显式用 {@link JsonSupport#objectMapper()}，否则 standalone 默认的
 * Jackson 是 camelCase，与线上 {@code spring.jackson.property-naming-strategy} 不一致。
 */
final class ApiTestSupport implements AutoCloseable {

    final Path dir;
    final LearningStore store;
    final SettingsStore settings;
    final KnowledgeStore knowledgeStore;
    final KnowledgeService knowledgeService;
    final LearningService service;
    final MockMvc mvc;
    /** 探测端点收到的字段（供「掩码回退」用例断言）。 */
    final Map<String, Object> probeSeen = new LinkedHashMap<>();
    /** 探测端点的返回值；用例可替换。 */
    Map<String, Object> probeResult = Map.of("status", "connected", "models", List.of());
    /** fetch-models 收到的字段与返回值。 */
    final Map<String, Object> fetchSeen = new LinkedHashMap<>();
    Map<String, Object> fetchResult = Map.of("status", "connected", "models", List.of());
    /** 维度探测收到的字段与返回值。 */
    final Map<String, Object> dimensionSeen = new LinkedHashMap<>();
    Map<String, Object> dimensionResult = Map.of("status", "unavailable", "dimension", 0);

    ApiTestSupport() throws IOException {
        this.dir = Files.createTempDirectory("mp-api-");
        this.store = new LearningStore(dir);
        this.settings = new SettingsStore(dir);
        this.knowledgeStore = new KnowledgeStore(dir);
        this.knowledgeService = new KnowledgeService(knowledgeStore, settings,
                config -> texts -> deterministicVectors(texts));
        this.service = new LearningService(store);
        SettingsProbeController probeController = new SettingsProbeController(
                settings,
                (protocol, baseUrl, apiKey, service1) -> {
                    probeSeen.clear();
                    probeSeen.put("protocol", protocol);
                    probeSeen.put("base_url", baseUrl);
                    probeSeen.put("api_key", apiKey);
                    probeSeen.put("service", service1);
                    return probeResult;
                },
                (protocol, baseUrl, apiKey, service1) -> {
                    fetchSeen.clear();
                    fetchSeen.put("protocol", protocol);
                    fetchSeen.put("base_url", baseUrl);
                    fetchSeen.put("api_key", apiKey);
                    fetchSeen.put("service", service1);
                    return fetchResult;
                },
                (protocol, baseUrl, apiKey, model) -> {
                    dimensionSeen.clear();
                    dimensionSeen.put("protocol", protocol);
                    dimensionSeen.put("base_url", baseUrl);
                    dimensionSeen.put("api_key", apiKey);
                    dimensionSeen.put("model", model);
                    return dimensionResult;
                });
        ObjectMapper mapper = JsonSupport.objectMapper();
        this.mvc = MockMvcBuilders.standaloneSetup(
                        new LearningController(service, store, knowledgeService),
                        new QuestionBankController(service, store),
                        new QuestionBankPracticeController(new QuestionBankService(store), store),
                        new SessionController(service, store),
                        new StudyController(service, store),
                        new QuizSetController(new QuizSetService(store)),
                        new SettingsController(settings),
                        new KnowledgeController(knowledgeService),
                        probeController)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    /**
     * 确定性向量：同一文本永远得到同一向量，且共享词越多相似度越高。
     *
     * <p>用词袋哈希而不是随机数，测试因此可复现（参考实现用 {@code _fake_embedder} 同理）。
     */
    static List<List<Double>> deterministicVectors(List<String> texts) {
        List<List<Double>> vectors = new ArrayList<>();
        for (String text : texts) {
            double[] vector = new double[8];
            for (String word : text.toLowerCase().split("\\s+")) {
                if (word.isEmpty()) {
                    continue;
                }
                vector[Math.floorMod(word.hashCode(), 8)] += 1.0;
            }
            List<Double> values = new ArrayList<>();
            for (double value : vector) {
                values.add(value);
            }
            vectors.add(values);
        }
        return vectors;
    }

    /** 建一条含单个 memory 知识点的路径（{@code k1}）。 */
    void buildOutline(String pathId) {
        service.createPath(pathId, "learn X", "X");
        service.build(pathId, Map.of("modules", List.of(Map.of(
                "name", "M",
                "knowledge_points", List.of(Map.of("name", "fact one", "type", "memory"))))), false);
    }

    /** 建一条含 memory + concept 两个知识点的路径（{@code k1} / {@code k2}）。 */
    void buildMixedOutline(String pathId) {
        service.createPath(pathId, "learn X", "X");
        service.build(pathId, Map.of("modules", List.of(Map.of(
                "name", "M",
                "knowledge_points", List.of(
                        Map.of("name", "fact one", "type", "memory"),
                        Map.of("name", "deep idea", "type", "concept"))))), false);
    }

    @Override
    public void close() {
        store.close();
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时目录清理失败不影响断言结果。
                }
            });
        } catch (IOException ignored) {
            // 同上。
        }
    }
}
