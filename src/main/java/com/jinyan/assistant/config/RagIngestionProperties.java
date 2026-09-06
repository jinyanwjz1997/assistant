package com.jinyan.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "assistant.rag.ingestion")
public class RagIngestionProperties {

    private String streamKey = "rag:segment:stream";
    private String deadLetterStreamKey = "rag:segment:dead-letter";
    private String consumerGroup = "rag-summary-workers";
    private String consumerName;
    private String llmLockKey = "rag:summary:llm-lock";
    private int maxRetries = 3;
    private Duration readTimeout = Duration.ofSeconds(2);
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
