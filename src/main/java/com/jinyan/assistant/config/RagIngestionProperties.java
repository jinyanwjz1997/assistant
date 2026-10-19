package com.jinyan.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** RAG 异步入库相关的 Redis Stream、重试和消费超时配置。 */
@Component
@ConfigurationProperties(prefix = "assistant.rag.ingestion")
public class RagIngestionProperties {

    /** 待处理片段所在的主 Stream。 */
    private String streamKey = "rag:segment:stream";
    /** 超过最大重试次数后写入的死信 Stream。 */
    private String deadLetterStreamKey = "rag:segment:dead-letter";
    /** 同一组中的多个应用实例会共同分摊消息。 */
    private String consumerGroup = "rag-summary-workers";
    /** 为空时由应用使用 HOSTNAME 和 UUID 自动生成，保证实例间不重名。 */
    private String consumerName;
    /** 所有实例共用该分布式锁，将摘要 LLM 的全局并发限制为 1。 */
    private String llmLockKey = "rag:summary:llm-lock";
    /** 首次处理失败后允许再次投递的次数。 */
    private int maxRetries = 3;
    /** 没有新消息时，阻塞读取 Stream 的最长时间。 */
    private Duration readTimeout = Duration.ofSeconds(2);
    /** Pending 消息超过该空闲时间后，可由其他消费者接管。 */
    private Duration pendingIdleTimeout = Duration.ofSeconds(60);

    public String getStreamKey() {
        return streamKey;
    }

    public void setStreamKey(String streamKey) {
        this.streamKey = streamKey;
    }

    public String getDeadLetterStreamKey() {
        return deadLetterStreamKey;
    }

    public void setDeadLetterStreamKey(String deadLetterStreamKey) {
        this.deadLetterStreamKey = deadLetterStreamKey;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    public String getLlmLockKey() {
        return llmLockKey;
    }

    public void setLlmLockKey(String llmLockKey) {
        this.llmLockKey = llmLockKey;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getPendingIdleTimeout() {
        return pendingIdleTimeout;
    }

    public void setPendingIdleTimeout(Duration pendingIdleTimeout) {
        this.pendingIdleTimeout = pendingIdleTimeout;
    }
}
