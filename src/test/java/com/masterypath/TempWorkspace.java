package com.masterypath;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 每个 Spring 上下文一个独立的数据目录（{@code masterypath.data-dir}）。
 *
 * <p>固定目录会让 {@code @SpringBootTest} 跨运行复用同一个 SQLite 库：上一次留下的
 * 会话/路径会污染下一次，{@code WsTurnTest} 不得不靠随机 id 规避。这里每次运行新建
 * 一个子目录，跨运行完全隔离。
 *
 * <p>目录放在 {@code target/test-workspace/} 下：随 {@code mvnw clean} 一起消失，
 * 不污染系统临时目录，也不会进入版本库。
 *
 * <p>不用 {@code application.properties} 里的 {@code ${random.uuid}}：该占位符每次解析
 * 都会生成新值，而 {@code WebConfig} 有四个 {@code @Bean} 分别注入同一个属性，
 * 结果会拿到四个不同目录。
 */
public final class TempWorkspace {

    /** 构建目录下的工作区根；{@code mvnw clean} 会一并删除。 */
    private static final Path BASE = Path.of("target", "test-workspace");

    private TempWorkspace() {
    }

    /**
     * 创建一个独立子目录并注册为 {@code masterypath.data-dir}。
     *
     * @param registry Spring 的动态属性注册表
     */
    public static void registerDataDir(DynamicPropertyRegistry registry) {
        Path directory = create();
        registry.add("masterypath.data-dir", directory::toString);
    }

    private static Path create() {
        try {
            Files.createDirectories(BASE);
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            return Files.createDirectory(BASE.resolve("run-" + suffix));
        } catch (IOException e) {
            throw new IllegalStateException("could not create a test workspace under " + BASE, e);
        }
    }
}
