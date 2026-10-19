package com.jinyan.assistant.service.impl;

import com.jinyan.assistant.config.RagIngestionProperties;
import com.jinyan.assistant.service.SegmentSummaryService;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 使用 DeepSeek 为 RAG 子片段生成简短摘要。 */
@Service
@Slf4j
public class SegmentSummaryServiceImpl implements SegmentSummaryService {

    private static final int FALLBACK_SUMMARY_MAX_CHARACTERS = 200;

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

    /**
     * 创建摘要服务。
     *
     * @param chatModel 摘要模型
     * @param redissonClient Redis 客户端
     * @param properties RAG Redis 配置
     */
    public SegmentSummaryServiceImpl(
            @Qualifier("summaryChatModel") ChatModel chatModel,
            RedissonClient redissonClient,
            RagIngestionProperties properties
    ) {
        this.chatModel = chatModel;
        this.redissonClient = redissonClient;
        this.properties = properties;
    }

    /**
     * 在 Redis 分布式锁保护下生成摘要。
     *
     * @param text 原始片段文本
     * @return 摘要正文
     * @throws InterruptedException 等待锁时线程被中断
     */
    @Override
    public String summarize(String text) throws InterruptedException {
        // 本地单线程只能限制单个实例；Redis 锁可以限制整个集群的 LLM 并发。
        RLock llmLock = redissonClient.getLock(properties.getLlmLockKey());
        llmLock.lockInterruptibly();
        try {
            String summary = chatModel.chat(SUMMARY_PROMPT.formatted(text));
            if (!StringUtils.hasText(summary)) {
                // 空正文通常是推理模型只返回 reasoning_content，并非值得重复请求的临时异常。
                // 摘要是检索增强信息，不能因为它为空而阻断原文向量化和整个文档入库。
                String fallback = createFallbackSummary(text);
                log.warn("摘要模型返回空正文，使用抽取式摘要继续入库, fallbackLength:{}", fallback.length());
                return fallback;
            }
            return summary.trim();
        } finally {
            // lockInterruptibly 可能在加锁前被中断，因此只释放当前线程真正持有的锁。
            if (llmLock.isHeldByCurrentThread()) {
                llmLock.unlock();
            }
        }
    }

    /**
     * 从原文开头提取一个尽量完整的语义片段作为兜底摘要。
     *
     * @param text 原始片段文本
     * @return 不超过 200 个字符的非空摘要
     * @throws IllegalArgumentException 当原始片段为空时抛出
     */
    static String createFallbackSummary(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("Cannot summarize blank segment text");
        }

        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= FALLBACK_SUMMARY_MAX_CHARACTERS) {
            return normalized;
        }

        int boundary = -1;
        for (int index = FALLBACK_SUMMARY_MAX_CHARACTERS - 1; index >= 80; index--) {
            char value = normalized.charAt(index);
            if (value == '。' || value == '！' || value == '？'
                    || value == '.' || value == '!' || value == '?') {
                boundary = index + 1;
                break;
            }
        }
        int end = boundary > 0 ? boundary : FALLBACK_SUMMARY_MAX_CHARACTERS;
        return normalized.substring(0, end).trim();
    }

}
