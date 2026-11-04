package com.masterypath.adapter.web;

import com.masterypath.service.LearningService;
import com.masterypath.service.NavigationService;
import com.masterypath.storage.LearningStore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * 传输层依赖装配。
 *
 * <p>参考实现把 {@code LearningStore} 作为参数注入 {@code create_app(store, ...)}；
 * Java 侧用 {@code @Bean} 表达同一件事：一个 workspace 一个库（{@code INV-52}）。
 *
 * <p>工作目录由 {@code masterypath.data-dir} 指定，默认 {@code data}（与参考实现的
 * {@code data/} 布局一致，已在 {@code .gitignore} 中排除）。
 */
@Configuration
public class WebConfig {

    @Bean
    public LearningStore learningStore(@Value("${masterypath.data-dir:data}") String dataDir) {
        return new LearningStore(Path.of(dataDir));
    }

    @Bean
    public LearningService learningService(LearningStore store) {
        return new LearningService(store);
    }

    @Bean
    public NavigationService navigationService(LearningStore store) {
        return new NavigationService(store);
    }
}
