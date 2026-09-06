package com.jinyan.assistant.config;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfiguration {

    @Bean
    public OpenAiChatModel chatLanguageModel() {

        // 注入Agent
        return OpenAiChatModel.builder().apiKey(DeepSeekConfig.getApiKey())
            .baseUrl(DeepSeekConfig.getBaseUrl())
            .modelName(DeepSeekConfig.getModelName())
            .maxTokens(DeepSeekConfig.getMaxTokens())
            .logRequests(true)
            .logResponses(true)
            .build();
    }

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.withMaxMessages(10);
    }
}
