package com.jinyan.assistant.worker;

import com.jinyan.assistant.config.RagIngestionProperties;
import com.jinyan.assistant.entity.RagSegmentMessage;
import com.jinyan.assistant.service.RedisStreamRagSegmentQueue;
import com.jinyan.assistant.service.RedisStreamRagSegmentQueue.StreamEntry;
import com.jinyan.assistant.service.SegmentSummaryService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 后台消费 RAG 片段：生成摘要、生成向量、写入 pgvector，最后确认 Stream 消息。
 *
 * <p>每个应用实例只创建一个消费线程；集群级 LLM 并发由摘要服务中的 Redis 锁控制。
 */
@Component
@Slf4j
public class RagSegmentStreamConsumer implements SmartLifecycle {

    private final RedisStreamRagSegmentQueue queue;
    private final SegmentSummaryService summaryService;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final RedissonClient redissonClient;
    private final RagIngestionProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final String consumerName;

    private ExecutorService executor;

    /**
     * 创建 RAG Stream 消费者。
     *
     * @param queue Redis Stream 队列
     * @param summaryService 摘要服务
     * @param embeddingModel 向量模型
     * @param embeddingStore 向量存储
     * @param redissonClient Redis 客户端
     * @param properties RAG 配置
     */
    public RagSegmentStreamConsumer(
            RedisStreamRagSegmentQueue queue,
            SegmentSummaryService summaryService,
            EmbeddingModel embeddingModel,
            @Qualifier("knowledgeEmbeddingStore") EmbeddingStore<TextSegment> embeddingStore,
            RedissonClient redissonClient,
            RagIngestionProperties properties
    ) {
        this.queue = queue;
        this.summaryService = summaryService;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.redissonClient = redissonClient;
        this.properties = properties;
        this.consumerName = resolveConsumerName(properties.getConsumerName());
    }

    /** 启动后台消费线程并确保消费组存在。 */
    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        queue.ensureConsumerGroup();
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "rag-stream-" + consumerName);
            thread.setDaemon(true);
            return thread;
        });
        executor.submit(this::consume);
        log.info("RAG Stream消费者已启动, consumerName:{}", consumerName);
    }

    /** 停止后台消费线程；未确认消息保留给后续接管。 */
    @Override
    public void stop() {
        running.set(false);
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * 判断后台消费者是否运行中。
     *
     * @return 正在运行时返回 {@code true}
     */
    @Override
    public boolean isRunning() {
        return running.get();
    }

    /** 循环接管超时 Pending 消息并读取新消息，直到消费者停止或线程被中断。 */
    private void consume() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // 优先恢复超时 Pending 消息，再阻塞读取新消息。
                queue.claimStale(consumerName).ifPresentOrElse(
                        this::processSafely,
                        () -> queue.readNew(consumerName).forEach(this::processSafely)
                );
            } catch (Exception exception) {
                if (Thread.currentThread().isInterrupted() || !running.get()) {
                    return;
                }
                log.error("读取RAG Redis Stream失败", exception);
                sleepBeforeRetry();
            }
        }
    }

    /**
     * 在片段锁保护下执行摘要、向量化、入库和 ACK。
     *
     * @param entry 待处理 Stream 消息
     */
    private void processSafely(StreamEntry entry) {
        RagSegmentMessage message = entry.message();
        // 防止重试副本或不同实例同时处理同一个 segmentId。
        RLock segmentLock = redissonClient.getLock("rag:segment:process-lock:" + message.segmentId());
        try {
            segmentLock.lockInterruptibly();
            if (queue.isSegmentFinished(message)) {
                queue.acknowledge(entry.id());
                return;
            }

            queue.markProcessing(message.fileId());
            String summary = summaryService.summarize(message.text());
            TextSegment segment = createSegment(message, summary);
            // 标题只在向量化时提供语义上下文，不写进正文，避免检索结果和摘要重复相同开头。
            Embedding embedding = embeddingModel.embed(toEmbeddingText(message)).content();

            // 使用确定性 segmentId 写入。重复执行时更新同一条向量，而不是新增重复数据。
            embeddingStore.addAll(
                    List.of(message.segmentId()),
                    List.of(embedding),
                    List.of(segment)
            );

            // 只有摘要、向量化和数据库写入全部成功后才 ACK。
            queue.markCompleted(message);
            queue.acknowledge(entry.id());
            log.info("RAG片段处理完成, fileId:{}, segment:{}/{}",
                    message.fileId(), message.segmentIndex() + 1, message.segmentCount());
        } catch (InterruptedException exception) {
            // 停机中断时不 ACK，消息保留在 Pending 中，稍后由其他消费者恢复。
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            handleFailure(entry, exception);
        } finally {
            if (segmentLock.isHeldByCurrentThread()) {
                segmentLock.unlock();
            }
        }
    }

    /**
     * 将 RAG 消息转换为带摘要及父子关系元数据的向量片段。
     *
     * @param message RAG 片段消息
     * @param summary 模型生成的摘要
     * @return 待写入向量库的片段
     */
    private TextSegment createSegment(RagSegmentMessage message, String summary) {
        // 原文用于向量化和召回，摘要只作为 metadata 保存，不替换原文。
        Metadata metadata = Metadata.from("source", message.source())
                .put("contentType", message.contentType())
                .put("fileId", message.fileId())
                .put("documentId", message.fileId())
                .put("segmentId", message.segmentId())
                .put("segmentIndex", message.segmentIndex())
                .put("segmentCount", message.segmentCount())
                .put("parentSectionId", message.parentSectionId())
                .put("parentText", message.parentText())
                .put("heading", message.heading())
                .put("headingPath", message.headingPath())
                .put("pageNumber", message.pageNumber())
                .put("previousSegmentId", message.previousSegmentId())
                .put("nextSegmentId", message.nextSegmentId())
                .put("summary", summary);
        return TextSegment.from(message.text(), metadata);
    }

    /**
     * 组合仅用于向量化的上下文文本，增强标题语义但不污染实际保存的片段正文。
     *
     * @param message RAG 片段消息
     * @return 用于生成 embedding 的标题路径和正文
     */
    private String toEmbeddingText(RagSegmentMessage message) {
        if (!StringUtils.hasText(message.headingPath())) {
            return message.text();
        }
        return message.headingPath() + "\n\n" + message.text();
    }

    /**
     * 根据当前重试次数选择重新投递或转入死信队列。
     *
     * @param entry 失败的 Stream 消息
     * @param exception 处理异常
     */
    private void handleFailure(StreamEntry entry, Exception exception) {
        RagSegmentMessage message = entry.message();
        if (message.retryCount() < properties.getMaxRetries()) {
            log.warn("RAG片段处理失败，将重试, fileId:{}, segmentIndex:{}, retryCount:{}",
                    message.fileId(), message.segmentIndex(), message.retryCount() + 1, exception);
            queue.retry(entry);
            return;
        }

        // 重试耗尽后转入死信队列，避免问题片段无限占用消费者。
        log.error("RAG片段重试耗尽，转入死信队列, fileId:{}, segmentIndex:{}",
                message.fileId(), message.segmentIndex(), exception);
        queue.moveToDeadLetter(entry, exception);
    }

    /** 暂停一秒后重试读取 Redis；中断时恢复线程中断标记。 */
    private void sleepBeforeRetry() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 解析消费者名称，未配置时使用主机名和随机 ID 生成实例唯一名称。
     *
     * @param configuredName 配置中的消费者名称
     * @return 实际使用的消费者名称
     */
    private String resolveConsumerName(String configuredName) {
        if (StringUtils.hasText(configuredName)) {
            return configuredName;
        }
        String hostName = System.getenv().getOrDefault("HOSTNAME", "local");
        return hostName + "-" + UUID.randomUUID();
    }
}
