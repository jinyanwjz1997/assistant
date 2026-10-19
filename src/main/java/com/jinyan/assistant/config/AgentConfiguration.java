package com.jinyan.assistant.config;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** 配置普通对话和 RAG 摘要使用的 DeepSeek 模型。 */
@Configuration
public class AgentConfiguration {

    /**
     * 创建普通对话模型，并将其作为 AI Service 默认模型。
     *
     * @param config DeepSeek 配置
     * @return 普通对话模型
     */
    @Bean
    @Primary
    public OpenAiChatModel chatLanguageModel(DeepSeekConfig config) {
        return baseBuilder(config)
                .maxTokens(config.getMaxTokens())
                .timeout(config.getTimeout())
                .maxRetries(config.getMaxRetries())
                .build();
    }

    /**
     * 创建 RAG 摘要专用模型。摘要响应较短，并由 Redis Stream 统一管理重试。
     *
     * @param config DeepSeek 配置
     * @return 摘要专用模型
     */
    @Bean("summaryChatModel")
    public OpenAiChatModel summaryChatModel(DeepSeekConfig config) {
        return baseBuilder(config)
                .modelName(config.getSummaryModelName())
                .maxTokens(config.getSummaryMaxTokens())
                .timeout(config.getSummaryTimeout())
                .maxRetries(config.getSummaryMaxRetries())
                .build();
    }

    /**
     * 创建按配置保留最近消息的内存会话窗口。
     *
     * @param properties 记忆配置
     * @return 聊天记忆组件
     */
    @Bean
    public ChatMemory chatMemory(MemoryProperties properties) {
        return MessageWindowChatMemory.withMaxMessages(properties.getShortTermMaxMessages());
    }

    /**
     * 创建带公共连接参数和日志设置的模型构建器。
     *
     * @param config DeepSeek 配置
     * @return 尚未设置请求用途参数的模型构建器
     */
    private OpenAiChatModel.OpenAiChatModelBuilder baseBuilder(DeepSeekConfig config) {
        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .logRequests(true)
                .logResponses(true);
    }
}
