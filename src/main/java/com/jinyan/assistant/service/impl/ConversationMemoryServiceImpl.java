package com.jinyan.assistant.service.impl;

import com.jinyan.assistant.config.MemoryProperties;
import com.jinyan.assistant.entity.MemoryContext;
import com.jinyan.assistant.entity.ShortTermMemory;
import com.jinyan.assistant.service.ConversationMemoryService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 默认会话记忆实现：本地窗口保存短期消息，pgvector 保存长期问答记忆。 */
@Service
public class ConversationMemoryServiceImpl implements ConversationMemoryService {

    private static final String MEMORY_TYPE = "conversation_memory";
    private static final int MAX_SHORT_TERM_MESSAGES = 100;
    private static final int MAX_LONG_TERM_RESULTS = 20;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> memoryEmbeddingStore;
    private final MemoryProperties properties;
    private final ConcurrentMap<String, ChatMemory> memories = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> memoryLocks = new ConcurrentHashMap<>();

    /**
     * 创建会话记忆服务。
     *
     * @param embeddingModel 文本向量模型
     * @param memoryEmbeddingStore 长期记忆向量表
     * @param properties 记忆配置
     */
    public ConversationMemoryServiceImpl(
            EmbeddingModel embeddingModel,
            @Qualifier("userEmbeddingStore") EmbeddingStore<TextSegment> memoryEmbeddingStore,
            MemoryProperties properties
    ) {
        this.embeddingModel = embeddingModel;
        this.memoryEmbeddingStore = memoryEmbeddingStore;
        this.properties = properties;
        validateProperties(properties);
    }

    /**
     * 返回指定会话的滑动窗口消息。
     *
     * @param conversationId 会话 ID
     * @return 当前短期记忆快照
     */
    @Override
    public ShortTermMemory shortTermMemory(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return new ShortTermMemory("", List.of());
        }
        synchronized (lockFor(conversationId)) {
            List<String> messages = new ArrayList<>();
            for (ChatMessage message : memory(conversationId).messages()) {
                if (message instanceof UserMessage userMessage) {
                    messages.add("用户：" + userMessage.singleText());
                } else if (message instanceof AiMessage aiMessage && StringUtils.hasText(aiMessage.text())) {
                    messages.add("助手：" + aiMessage.text());
                }
            }
            return new ShortTermMemory(conversationId, List.copyOf(messages));
        }
    }

    /**
     * 按会话 ID 过滤长期向量记忆。
     *
     * @param conversationId 会话 ID
     * @param query 当前问题
     * @param maxResults 最大结果数
     * @return 相似度排序后的记忆结果
     */
    @Override
    public List<MemoryContext> searchLongTermMemory(
            String conversationId,
            String query,
            int maxResults
    ) {
        validateConversationId(conversationId);
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (!properties.isLongTermEnabled()) {
            return List.of();
        }
        if (maxResults < 1) {
            throw new IllegalArgumentException("maxResults must be positive");
        }

        int resultLimit = Math.min(
                Math.min(maxResults, properties.getLongTermMaxResults()),
                MAX_LONG_TERM_RESULTS
        );
        Filter conversationFilter = MetadataFilterBuilder.metadataKey("conversationId")
                .isEqualTo(conversationId);
        Filter memoryTypeFilter = MetadataFilterBuilder.metadataKey("memoryType")
                .isEqualTo(MEMORY_TYPE);
        var result = memoryEmbeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(query).content())
                .maxResults(resultLimit)
                .minScore(properties.getLongTermMinScore())
                .filter(conversationFilter.and(memoryTypeFilter))
                .build());

        List<MemoryContext> memories = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            Metadata metadata = match.embedded().metadata();
            memories.add(new MemoryContext(
                    memories.size() + 1,
                    match.score(),
                    match.embedded().text(),
                    metadata.getString("conversationId"),
                    parseInstant(metadata.getString("createdAt"))
            ));
        }
        return List.copyOf(memories);
    }

    /**
     * 保存一轮问答到短期窗口和长期向量库。
     *
     * @param conversationId 会话 ID
     * @param question 用户问题
     * @param answer 助手完整答案
     */
    @Override
    public void remember(String conversationId, String question, String answer) {
        validateConversationId(conversationId);
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("question must not be blank");
        }
        if (answer == null) {
            throw new IllegalArgumentException("answer must not be null");
        }

        synchronized (lockFor(conversationId)) {
            memory(conversationId).add(UserMessage.from(question));
            memory(conversationId).add(AiMessage.from(answer));
            if (!properties.isLongTermEnabled()) {
                return;
            }

            String createdAt = Instant.now().toString();
            String text = "用户操作：\n" + question.trim() + "\n\n助手答案：\n" + answer.trim();
            Metadata metadata = Metadata.from("memoryType", MEMORY_TYPE)
                    .put("conversationId", conversationId)
                    .put("createdAt", createdAt)
                    .put("question", question.trim())
                    .put("answer", answer.trim());
            memoryEmbeddingStore.addAll(
                    List.of(UUID.randomUUID().toString()),
                    List.of(embeddingModel.embed(text).content()),
                    List.of(TextSegment.from(text, metadata))
            );
        }
    }

    /**
     * 获取或创建会话窗口。
     *
     * @param conversationId 会话 ID
     * @return 会话窗口
     */
    private ChatMemory memory(String conversationId) {
        return memories.computeIfAbsent(conversationId, id -> MessageWindowChatMemory.builder()
                .id(id)
                .maxMessages(properties.getShortTermMaxMessages())
                .build());
    }

    /**
     * 获取指定会话的串行化锁。
     *
     * <p>同一会话的并发请求必须按完整问答轮次更新窗口，否则两个请求可能交叉写入，
     * 导致短期记忆出现“用户 A、用户 B、答案 A、答案 B”的错序内容。
     *
     * @param conversationId 会话 ID
     * @return 会话级锁对象
     */
    private Object lockFor(String conversationId) {
        return memoryLocks.computeIfAbsent(conversationId, ignored -> new Object());
    }

    /**
     * 校验配置，避免滑动窗口配置错误导致应用启动后运行时异常。
     *
     * @param memoryProperties 记忆配置
     */
    private void validateProperties(MemoryProperties memoryProperties) {
        if (memoryProperties.getShortTermMaxMessages() < 2
                || memoryProperties.getShortTermMaxMessages() > MAX_SHORT_TERM_MESSAGES) {
            throw new IllegalArgumentException("shortTermMaxMessages must be between 2 and 100");
        }
        if (memoryProperties.getLongTermMaxResults() < 1
                || memoryProperties.getLongTermMaxResults() > MAX_LONG_TERM_RESULTS) {
            throw new IllegalArgumentException("longTermMaxResults must be between 1 and 20");
        }
        if (memoryProperties.getLongTermMinScore() < 0 || memoryProperties.getLongTermMinScore() > 1) {
            throw new IllegalArgumentException("longTermMinScore must be between 0 and 1");
        }
    }

    /**
     * 校验会话 ID。
     *
     * @param conversationId 会话 ID
     */
    private void validateConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        if (conversationId.length() > 128) {
            throw new IllegalArgumentException("conversationId must not exceed 128 characters");
        }
    }

    /**
     * 安全解析创建时间。
     *
     * @param value 时间文本
     * @return 解析结果；无法解析时返回 {@code null}
     */
    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
