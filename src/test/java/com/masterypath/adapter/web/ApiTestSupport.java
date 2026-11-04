package com.masterypath.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;
import com.masterypath.service.LearningService;
import com.masterypath.storage.LearningStore;

import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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
    final LearningService service;
    final MockMvc mvc;

    ApiTestSupport() throws IOException {
        this.dir = Files.createTempDirectory("mp-api-");
        this.store = new LearningStore(dir);
        this.service = new LearningService(store);
        ObjectMapper mapper = JsonSupport.objectMapper();
        this.mvc = MockMvcBuilders.standaloneSetup(
                        new LearningController(service, store),
                        new QuestionBankController(service, store),
                        new SessionController(service, store),
                        new StudyController(service, store),
                        new SettingsController())
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
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
