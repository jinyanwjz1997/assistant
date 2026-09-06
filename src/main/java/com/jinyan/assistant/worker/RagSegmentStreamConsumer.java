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

    @Override
    public void stop() {
        running.set(false);
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private void consume() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
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

    private void processSafely(StreamEntry entry) {
        RagSegmentMessage message = entry.message();
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
            Embedding embedding = embeddingModel.embed(segment).content();
            embeddingStore.addAll(
                    List.of(message.segmentId()),
                    List.of(embedding),
                    List.of(segment)
            );
            queue.markCompleted(message);
            queue.acknowledge(entry.id());
            log.info("RAG片段处理完成, fileId:{}, segment:{}/{}",
                    message.fileId(), message.segmentIndex() + 1, message.segmentCount());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            handleFailure(entry, exception);
        } finally {
            if (segmentLock.isHeldByCurrentThread()) {
                segmentLock.unlock();
            }
        }
    }

    private TextSegment createSegment(RagSegmentMessage message, String summary) {
        Metadata metadata = Metadata.from("source", message.source())
                .put("contentType", message.contentType())
                .put("fileId", message.fileId())
                .put("documentId", message.fileId())
                .put("segmentId", message.segmentId())
                .put("segmentIndex", message.segmentIndex())
                .put("segmentCount", message.segmentCount())
                .put("summary", summary);
        return TextSegment.from(message.text(), metadata);
    }

    private void handleFailure(StreamEntry entry, Exception exception) {
        RagSegmentMessage message = entry.message();
        if (message.retryCount() < properties.getMaxRetries()) {
            log.warn("RAG片段处理失败，将重试, fileId:{}, segmentIndex:{}, retryCount:{}",
                    message.fileId(), message.segmentIndex(), message.retryCount() + 1, exception);
            queue.retry(entry);
            return;
        }

        log.error("RAG片段重试耗尽，转入死信队列, fileId:{}, segmentIndex:{}",
                message.fileId(), message.segmentIndex(), exception);
        queue.moveToDeadLetter(entry, exception);
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String resolveConsumerName(String configuredName) {
        if (StringUtils.hasText(configuredName)) {
            return configuredName;
        }
        String hostName = System.getenv().getOrDefault("HOSTNAME", "local");
        return hostName + "-" + UUID.randomUUID();
    }
}
