package com.jinyan.assistant.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AgentConfigurationTest {

    @Test
    void shouldCreateChatAndSummaryModelsWithConfiguredTimeouts() {
        DeepSeekConfig config = new DeepSeekConfig();
        config.setApiKey("test-api-key");
        config.setBaseUrl("https://example.test");
        config.setModelName("test-model");
        config.setMaxTokens(4096);
        config.setTimeout(Duration.ofSeconds(120));
        config.setMaxRetries(2);
        config.setSummaryTimeout(Duration.ofSeconds(120));
        config.setSummaryModelName("deepseek-chat");
        config.setSummaryMaxTokens(1024);
        config.setSummaryMaxRetries(0);

        AgentConfiguration configuration = new AgentConfiguration();

        assertThat(configuration.chatLanguageModel(config)).isNotNull();
        assertThat(configuration.summaryChatModel(config)).isNotNull();
    }
}
