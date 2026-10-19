package com.jinyan.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** DeepSeek 聊天模型及 RAG 摘要模型配置。 */
@Component
@ConfigurationProperties(prefix = "langchain.deep-seek")
public class DeepSeekConfig {

    /** DeepSeek API 密钥。 */
    private String apiKey;

    /** OpenAI 兼容接口地址。 */
    private String baseUrl;

    /** 默认聊天模型名称。 */
    private String modelName;

    /** 普通聊天请求的最大输出 Token 数。 */
    private Integer maxTokens;

    /** 普通聊天请求超时时间。 */
    private Duration timeout = Duration.ofSeconds(120);

    /** LangChain4j 普通聊天请求的内部重试次数。 */
    private int maxRetries = 2;

    /** RAG 片段摘要请求超时时间。 */
    private Duration summaryTimeout = Duration.ofSeconds(120);

    /** RAG 摘要使用的非推理模型名称。 */
    private String summaryModelName = "deepseek-chat";

    /** RAG 片段摘要的最大输出 Token 数。 */
    private int summaryMaxTokens = 1024;

    /**
     * RAG 摘要关闭模型客户端内部重试，统一由 Redis Stream 负责可靠重试。
     */
    private int summaryMaxRetries = 0;

    /**
     * 获取 API 密钥。
     *
     * @return API 密钥
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 设置 API 密钥。
     *
     * @param apiKey API 密钥
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 获取接口地址。
     *
     * @return OpenAI 兼容接口地址
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 设置接口地址。
     *
     * @param baseUrl OpenAI 兼容接口地址
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 获取模型名称。
     *
     * @return 模型名称
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * 设置模型名称。
     *
     * @param modelName 模型名称
     */
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    /**
     * 获取普通聊天最大输出 Token 数。
     *
     * @return 最大输出 Token 数
     */
    public Integer getMaxTokens() {
        return maxTokens;
    }

    /**
     * 设置普通聊天最大输出 Token 数。
     *
     * @param maxTokens 最大输出 Token 数
     */
    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    /**
     * 获取普通聊天请求超时时间。
     *
     * @return 请求超时时间
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * 设置普通聊天请求超时时间。
     *
     * @param timeout 请求超时时间
     */
    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    /**
     * 获取普通聊天请求的内部重试次数。
     *
     * @return 内部重试次数
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * 设置普通聊天请求的内部重试次数。
     *
     * @param maxRetries 内部重试次数
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    /**
     * 获取摘要请求超时时间。
     *
     * @return 摘要请求超时时间
     */
    public Duration getSummaryTimeout() {
        return summaryTimeout;
    }

    /**
     * 设置摘要请求超时时间。
     *
     * @param summaryTimeout 摘要请求超时时间
     */
    public void setSummaryTimeout(Duration summaryTimeout) {
        this.summaryTimeout = summaryTimeout;
    }

    /**
     * 获取摘要模型名称。
     *
     * @return 摘要模型名称
     */
    public String getSummaryModelName() {
        return summaryModelName;
    }

    /**
     * 设置摘要模型名称。
     *
     * @param summaryModelName 摘要模型名称
     */
    public void setSummaryModelName(String summaryModelName) {
        this.summaryModelName = summaryModelName;
    }

    /**
     * 获取摘要最大输出 Token 数。
     *
     * @return 摘要最大输出 Token 数
     */
    public int getSummaryMaxTokens() {
        return summaryMaxTokens;
    }

    /**
     * 设置摘要最大输出 Token 数。
     *
     * @param summaryMaxTokens 摘要最大输出 Token 数
     */
    public void setSummaryMaxTokens(int summaryMaxTokens) {
        this.summaryMaxTokens = summaryMaxTokens;
    }

    /**
     * 获取摘要模型客户端内部重试次数。
     *
     * @return 摘要内部重试次数
     */
    public int getSummaryMaxRetries() {
        return summaryMaxRetries;
    }

    /**
     * 设置摘要模型客户端内部重试次数。
     *
     * @param summaryMaxRetries 摘要内部重试次数
     */
    public void setSummaryMaxRetries(int summaryMaxRetries) {
        this.summaryMaxRetries = summaryMaxRetries;
    }
}
