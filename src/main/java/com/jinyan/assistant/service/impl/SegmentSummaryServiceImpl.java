package com.jinyan.assistant.service.impl;

import com.jinyan.assistant.config.RagIngestionProperties;
import com.jinyan.assistant.service.SegmentSummaryService;
import dev.langchain4j.model.chat.ChatModel;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SegmentSummaryServiceImpl implements SegmentSummaryService {

    private static final String SUMMARY_PROMPT = """
            请对下面的知识库片段生成简洁、准确的摘要。
            要求：
            1. 保留关键事实、名称、数字和约束。
            2. 不补充原文没有的信息。
            3. 只输出摘要正文，不要添加标题。
            4. 控制在120个中文字符以内。

            原文：
            %s
            """;

    private final ChatModel chatModel;
    private final RedissonClient redissonClient;
    private final RagIngestionProperties properties;

    public SegmentSummaryServiceImpl(
            @Qualifier("chatLanguageModel") ChatModel chatModel,
            RedissonClient redissonClient,
            RagIngestionProperties properties
    ) {
        this.chatModel = chatModel;
        this.redissonClient = redissonClient;
        this.properties = properties;
    }

    @Override
    public String summarize(String text) throws InterruptedException {
        RLock llmLock = redissonClient.getLock(properties.getLlmLockKey());
        llmLock.lockInterruptibly();
        try {
            String summary = chatModel.chat(SUMMARY_PROMPT.formatted(text));
            if (!StringUtils.hasText(summary)) {
                throw new IllegalStateException("LLM returned an empty segment summary");
            }
            return summary.trim();
        } finally {
            if (llmLock.isHeldByCurrentThread()) {
                llmLock.unlock();
            }
        }
    }
}
