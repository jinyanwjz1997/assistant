package com.jinyan.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "langchain.deep-seek")
public class DeepSeekConfig {

    private static String apiKey;

    private static String baseUrl;

    private static String modelName;

    private static Integer maxTokens;

    public static String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        DeepSeekConfig.apiKey = apiKey;
    }

    public static String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        DeepSeekConfig.baseUrl = baseUrl;
    }

    public static String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        DeepSeekConfig.modelName = modelName;
    }

    public static Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        DeepSeekConfig.maxTokens = maxTokens;
    }
}
