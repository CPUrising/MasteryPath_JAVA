package com.masterypath.adapter.llm;

/**
 * 「模型选择 → {@link LlmClient}」的工厂（参考实现 {@code client_builder}）。
 *
 * <p>参考实现里它是闭包：`lambda selection=None, session_key="": build_client(config)`。
 * Java 侧抽成函数式接口，使 {@code TurnRunner} 不依赖设置存储——
 * 测试直接传 lambda，生产实现（读 {@code settings.json}）属 {@code 0022}。
 *
 * <p>{@code sessionKey} 用于供应商侧的会话路由头（{@code x-opencode-session}）。
 */
@FunctionalInterface
public interface ClientBuilder {

    /**
     * @param selection  模型选择（{@code {provider_id, model_id}}）；{@code null} 表示用默认
     * @param sessionKey 会话标识，可为空串
     * @return 可用的客户端
     */
    LlmClient build(Object selection, String sessionKey);
}
