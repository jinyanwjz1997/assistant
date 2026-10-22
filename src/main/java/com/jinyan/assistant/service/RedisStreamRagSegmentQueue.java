package com.jinyan.assistant.service;

import com.jinyan.assistant.config.RagIngestionProperties;
import com.jinyan.assistant.entity.RagIngestionStatus;
import com.jinyan.assistant.entity.RagSegmentMessage;
import com.jinyan.assistant.exception.ApiException;
import org.redisson.api.AutoClaimResult;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.RedisBusyException;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * RAG 片段的 Redis Stream 队列及任务状态存储。
 *
 * <p>主 Stream 负责可靠投递，Redis Set 记录片段终态，AtomicLong 记录任务进度。
 */
@Service
public class RedisStreamRagSegmentQueue {

    private static final String JOB_KEY_PREFIX = "rag:ingestion:job:";
    private static final String PROCESSED_KEY_PREFIX = "rag:ingestion:processed:";
    private static final String FAILED_KEY_PREFIX = "rag:ingestion:failed:";

    private final RedissonClient redissonClient;
    private final RagIngestionProperties properties;
    private final RStream<String, String> stream;

    /** 创建 RAG Stream 队列。
     * @param redissonClient Redisson 客户端
     * @param properties RAG Redis 配置
     */
    public RedisStreamRagSegmentQueue(
            RedissonClient redissonClient,
            RagIngestionProperties properties
    ) {
        this.redissonClient = redissonClient;
        this.properties = properties;
        this.stream = redissonClient.getStream(properties.getStreamKey(), StringCodec.INSTANCE);
    }

    /** 确保消费组存在。
     * @implNote 多实例并发创建时，已存在异常会被安全忽略。
     */
    public void ensureConsumerGroup() {
        try {
            // MKSTREAM 会在 Stream 不存在时同时创建它；NEWEST 表示从建组后的消息开始消费。
            stream.createGroup(properties.getConsumerGroup(), StreamMessageId.NEWEST);
        } catch (RedisBusyException ignored) {
            // The group was already created by this or another application instance.
        }
    }

    /** 将文档片段写入 Stream 并初始化任务状态。
     * @param fileId 文档唯一标识
     * @param segments 待处理片段
     */
    public void enqueue(String fileId, List<RagSegmentMessage> segments) {
        ensureConsumerGroup();

        // fileId 每次上传都会重新生成，因此初始化状态不会覆盖其他文档任务。
        RMap<String, String> job = job(fileId);
        job.put("status", "QUEUED");
        job.put("totalSegments", Integer.toString(segments.size()));
        processedCounter(fileId).set(0);
        failedCounter(fileId).set(0);
        processedSegments(fileId).clear();
        failedSegments(fileId).clear();

        if (segments.isEmpty()) {
            job.put("status", "COMPLETED");
            return;
        }
        for (RagSegmentMessage segment : segments) {
            stream.add(StreamAddArgs.entries(segment.toMap()));
        }
    }

    /** 读取消费组尚未投递的新消息。
     * @param consumerName 当前消费者名称
     * @return 新消息列表
     */
    public List<StreamEntry> readNew(String consumerName) {
        // neverDelivered 只读取尚未分配给组内任何消费者的新消息。
        Map<StreamMessageId, Map<String, String>> messages = stream.readGroup(
                properties.getConsumerGroup(),
                consumerName,
                StreamReadGroupArgs.neverDelivered()
                        .count(1)
                        .timeout(properties.getReadTimeout())
        );
        return toEntries(messages);
    }

    /** 接管长时间未确认的 Pending 消息。
     * @param consumerName 接管消息的消费者名称
     * @return 接管到的第一条消息，没有则为空
     */
    public Optional<StreamEntry> claimStale(String consumerName) {
        // 接管消费者崩溃后长期留在 Pending 列表中的消息，避免片段永久卡住。
        AutoClaimResult<String, String> result = stream.autoClaim(
                properties.getConsumerGroup(),
                consumerName,
                properties.getPendingIdleTimeout().toMillis(),
                java.util.concurrent.TimeUnit.MILLISECONDS,
                StreamMessageId.MIN,
                1
        );
        return toEntries(result.getMessages()).stream().findFirst();
    }

    /** 复制一条带新重试次数的消息并确认旧消息。
     * @param entry 原始 Stream 消息
     */
    public void retry(StreamEntry entry) {
        // 先写入新消息，再确认旧消息。写入失败时旧消息仍在 Pending 中，不会丢失。
        stream.add(StreamAddArgs.entries(entry.message().nextRetry().toMap()));
        acknowledge(entry.id());
    }

    /** 将处理失败且重试耗尽的消息写入死信 Stream。
     * @param entry 原始 Stream 消息
     * @param exception 最后一次处理异常
     */
    public void moveToDeadLetter(StreamEntry entry, Exception exception) {
        // 死信保留原消息和最后一次错误，便于后续排查或人工重新投递。
        Map<String, String> values = new LinkedHashMap<>(entry.message().toMap());
        values.put("error", abbreviate(exception.getMessage(), 1000));
        RStream<String, String> deadLetterStream = redissonClient.getStream(
                properties.getDeadLetterStreamKey(),
                StringCodec.INSTANCE
        );
        deadLetterStream.add(StreamAddArgs.entries(values));
        markFailed(entry.message());
        acknowledge(entry.id());
    }

    /** 确认并删除一条已完成的 Stream 消息。
     * @param id Stream 消息 ID
     */
    public void acknowledge(StreamMessageId id) {
        // ACK 先从消费组 Pending 列表移除，XDEL 再清理 Stream 本体，控制数据量。
        stream.ack(properties.getConsumerGroup(), id);
        stream.remove(id);
    }

    /** 将文档任务标记为处理中。
     * @param fileId 文档唯一标识
     */
    public void markProcessing(String fileId) {
        RMap<String, String> job = job(fileId);
        String currentStatus = job.get("status");
        if (!isFinalStatus(currentStatus)) {
            job.put("status", "PROCESSING");
        }
    }

    /** 标记片段成功并更新文档进度。
     * @param message 已完成的片段
     */
    public void markCompleted(RagSegmentMessage message) {
        // Set.add 只有首次加入时返回 true，防止重复消费导致进度重复累计。
        if (!processedSegments(message.fileId()).add(message.segmentId())) {
            return;
        }
        if (failedSegments(message.fileId()).remove(message.segmentId())) {
            failedCounter(message.fileId()).decrementAndGet();
        }
        processedCounter(message.fileId()).incrementAndGet();
        updateFinalStatus(message.fileId());
    }

    /** 判断片段是否已经成功或最终失败。
     * @param message 待判断片段
     * @return 是否存在终态记录
     */
    public boolean isSegmentFinished(RagSegmentMessage message) {
        // 已进入成功或最终失败状态的重复消息都不再调用模型。
        return processedSegments(message.fileId()).contains(message.segmentId())
                || failedSegments(message.fileId()).contains(message.segmentId());
    }

    /** 获取文档级入库状态。
     * @param fileId 文档唯一标识
     * @return 当前任务状态和进度
     */
    public RagIngestionStatus getStatus(String fileId) {
        RMap<String, String> job = job(fileId);
        String status = job.get("status");
        if (status == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "INGESTION_JOB_NOT_FOUND",
                    "文档入库任务不存在: " + fileId
            );
        }
        return new RagIngestionStatus(
                fileId,
                status,
                Long.parseLong(job.getOrDefault("totalSegments", "0")),
                processedCounter(fileId).get(),
                failedCounter(fileId).get()
        );
    }

    /** 记录片段最终失败并更新文档进度。
     * @param message 最终失败的片段
     */
    private void markFailed(RagSegmentMessage message) {
        if (processedSegments(message.fileId()).contains(message.segmentId())
                || !failedSegments(message.fileId()).add(message.segmentId())) {
            return;
        }
        failedCounter(message.fileId()).incrementAndGet();
        updateFinalStatus(message.fileId());
    }

    /** 根据成功和失败计数汇总文档最终状态。
     * @param fileId 文档唯一标识
     */
    private void updateFinalStatus(String fileId) {
        RMap<String, String> job = job(fileId);
        long total = Long.parseLong(job.getOrDefault("totalSegments", "0"));
        long processed = processedCounter(fileId).get();
        long failed = failedCounter(fileId).get();
        if (processed + failed < total) {
            return;
        }

        // 所有片段都有终态后，再根据成功和失败数量汇总文档级状态。
        if (failed == 0) {
            job.put("status", "COMPLETED");
        } else if (processed == 0) {
            job.put("status", "FAILED");
        } else {
            job.put("status", "PARTIALLY_FAILED");
        }
    }

    /** 将 Redisson Stream 消息转换为领域对象。
     * @param messages Redis 返回的消息映射
     * @return 领域消息列表
     */
    private List<StreamEntry> toEntries(Map<StreamMessageId, Map<String, String>> messages) {
        List<StreamEntry> entries = new ArrayList<>(messages.size());
        messages.forEach((id, values) ->
                entries.add(new StreamEntry(id, RagSegmentMessage.fromMap(values))));
        return entries;
    }

    /** 获取文档任务 Hash。
     * @param fileId 文档唯一标识
     * @return 任务状态 Hash
     */
    private RMap<String, String> job(String fileId) {
        return redissonClient.getMap(JOB_KEY_PREFIX + fileId, StringCodec.INSTANCE);
    }

    /** 获取成功片段计数器。
     * @param fileId 文档唯一标识
     * @return 成功计数器
     */
    private RAtomicLong processedCounter(String fileId) {
        return redissonClient.getAtomicLong(JOB_KEY_PREFIX + fileId + ":processed-count");
    }

    /** 获取失败片段计数器。
     * @param fileId 文档唯一标识
     * @return 失败计数器
     */
    private RAtomicLong failedCounter(String fileId) {
        return redissonClient.getAtomicLong(JOB_KEY_PREFIX + fileId + ":failed-count");
    }

    /** 获取成功片段集合。
     * @param fileId 文档唯一标识
     * @return 成功片段 ID 集合
     */
    private RSet<String> processedSegments(String fileId) {
        return redissonClient.getSet(PROCESSED_KEY_PREFIX + fileId, StringCodec.INSTANCE);
    }

    /** 获取失败片段集合。
     * @param fileId 文档唯一标识
     * @return 失败片段 ID 集合
     */
    private RSet<String> failedSegments(String fileId) {
        return redissonClient.getSet(FAILED_KEY_PREFIX + fileId, StringCodec.INSTANCE);
    }

    /** 判断任务状态是否已经结束。
     * @param status 任务状态
     * @return 是否为最终状态
     */
    private boolean isFinalStatus(String status) {
        return "COMPLETED".equals(status)
                || "PARTIALLY_FAILED".equals(status)
                || "FAILED".equals(status);
    }

    /** 截断异常信息，避免死信消息过大。
     * @param value 原始异常信息
     * @param maxLength 最大字符数
     * @return 截断后的文本
     */
    private String abbreviate(String value, int maxLength) {
        String safeValue = value == null ? "Unknown processing error" : value;
        return safeValue.length() <= maxLength
                ? safeValue
                : safeValue.substring(0, maxLength);
    }

    public record StreamEntry(StreamMessageId id, RagSegmentMessage message) {
    }
}
