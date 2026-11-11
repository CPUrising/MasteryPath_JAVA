package com.masterypath.adapter.web;

import com.masterypath.adapter.llm.ClientBuilder;
import com.masterypath.adapter.llm.ModelResolution;
import com.masterypath.adapter.llm.TaskModel;
import com.masterypath.adapter.turn.TurnRunner;
import com.masterypath.service.ChatService;
import com.masterypath.service.KnowledgeService;
import com.masterypath.service.LearningService;
import com.masterypath.service.NavigationService;
import com.masterypath.service.QuestionBankService;
import com.masterypath.service.QuizSetService;
import com.masterypath.storage.ChatStore;
import com.masterypath.storage.KnowledgeStore;
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

    /** 生成型练习题卷（{@code 0033}）。 */
    @Bean
    public QuizSetService quizSetService(LearningStore store) {
        return new QuizSetService(store);
    }

    /** 题库工作台：检索、标签管理、刷题（{@code 0046}）。 */
    @Bean
    public QuestionBankService questionBankService(LearningStore store) {
        return new QuestionBankService(store);
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

    /** 知识库存储（{@code knowledge_bases/}）。 */
    @Bean
    public KnowledgeStore knowledgeStore(@Value("${masterypath.data-dir:data}") String dataDir) {
        return new KnowledgeStore(Path.of(dataDir));
    }

    /** 知识库服务（索引、检索、参考块）。 */
    @Bean
    public KnowledgeService knowledgeService(KnowledgeStore knowledgeStore,
                                             SettingsStore settingsStore) {
        return new KnowledgeService(knowledgeStore, settingsStore);
    }

    /**
     * 普通聊天服务。
     *
     * <p>接上真实 {@link KnowledgeService}：命中时把「Reference material」块注入 system 消息。
     */
    @Bean
    public ChatService chatService(ChatStore chatStore, SettingsStore settingsStore,
                                   LearningService learningService,
                                   KnowledgeService knowledgeService) {
        return new ChatService(chatStore, settingsStore, knowledgeService, null, learningService);
    }

    /**
     * 任务模型（「建议下一问」草稿）。
     *
     * <p>工厂直接调 {@link ModelResolution#resolveLlmConfig}：未配置模型时它抛
     * {@code LlmError}，被 {@link TaskModel} 吞掉后回退到确定性文案——
     * 这正是「没有提供方也要能给出草稿」的语义。
     */
    @Bean
    public TaskModel taskModel(SettingsStore settingsStore) {
        return new TaskModel(() -> ModelResolution.buildClient(
                ModelResolution.resolveLlmConfig(settingsStore, null)));
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
                                 ChatService chatService, SettingsStore settingsStore,
                                 KnowledgeService knowledgeService) {
        ClientBuilder builder = (selection, sessionKey) -> {
            Map<String, Object> config = ModelResolution.resolveLlmConfig(settingsStore, selection);
            config.put("session_key", sessionKey);
            return ModelResolution.buildClient(config);
        };
        return new TurnRunner(service, store, builder, knowledgeService, chatService,
                settingsStore);
    }
}
