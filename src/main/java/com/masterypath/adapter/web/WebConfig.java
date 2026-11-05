package com.masterypath.adapter.web;

import com.masterypath.adapter.llm.ClientBuilder;
import com.masterypath.adapter.llm.LlmError;
import com.masterypath.adapter.turn.TurnRunner;
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

    /**
     * 回合编排器。
     *
     * <p>{@code knowledgeService} 传 {@code null}：知识库检索属 {@code 0021}。
     * 默认 {@link ClientBuilder} 直接报「未配置模型」——从设置解析模型属 {@code 0022}，
     * 在此之前任何真实回合都应当明确失败，而不是静默用一个空客户端。
     */
    @Bean
    public TurnRunner turnRunner(LearningService service, LearningStore store) {
        return new TurnRunner(service, store, unconfiguredClientBuilder(), null);
    }

    private static ClientBuilder unconfiguredClientBuilder() {
        return (selection, sessionKey) -> {
            throw new LlmError(
                    "No chat model is configured. Add one under Settings → Language models.",
                    "bad_request", false);
        };
    }
}
