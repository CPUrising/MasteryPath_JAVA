package com.masterypath.adapter.web;

import com.masterypath.adapter.llm.ClientBuilder;
import com.masterypath.adapter.llm.ModelResolution;
import com.masterypath.adapter.turn.TurnRunner;
import com.masterypath.service.ChatService;
import com.masterypath.service.LearningService;
import com.masterypath.service.NavigationService;
import com.masterypath.storage.ChatStore;
import com.masterypath.storage.LearningStore;
import com.masterypath.storage.SettingsStore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.Map;

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

    /** 聊天数据库（独立 {@code chat.db}，与 {@code learning.db} 同目录不共库）。 */
    @Bean
    public ChatStore chatStore(@Value("${masterypath.data-dir:data}") String dataDir) {
        return new ChatStore(Path.of(dataDir));
    }

    /** 设置存储（{@code settings.json}）。 */
    @Bean
    public SettingsStore settingsStore(@Value("${masterypath.data-dir:data}") String dataDir) {
        return new SettingsStore(Path.of(dataDir));
    }

    /**
     * 普通聊天服务。
     *
     * <p>{@code knowledgeService} 传 {@code null}：知识库检索属 {@code 0021}，
     * 在此之前参考块恒为空。
     */
    @Bean
    public ChatService chatService(ChatStore chatStore, SettingsStore settingsStore,
                                   LearningService learningService) {
        return new ChatService(chatStore, settingsStore, null, null, learningService);
    }

    /**
     * 回合编排器。
     *
     * <p>{@link ClientBuilder} 从设置解析模型（参考实现 {@code _build_llm_client}）：
     * 未配置时 {@link ModelResolution#resolveLlmConfig} 自己抛
     * {@code LlmError("No language model is configured...", "bad_request")}，
     * 回合因此以结构化错误结束，而不是静默用一个空客户端。
     */
    @Bean
    public TurnRunner turnRunner(LearningService service, LearningStore store,
                                 ChatService chatService, SettingsStore settingsStore) {
        ClientBuilder builder = (selection, sessionKey) -> {
            Map<String, Object> config = ModelResolution.resolveLlmConfig(settingsStore, selection);
            config.put("session_key", sessionKey);
            return ModelResolution.buildClient(config);
        };
        // knowledgeService 传 null：知识库检索属 0021。
        return new TurnRunner(service, store, builder, null, chatService);
    }
}
