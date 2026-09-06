package com.jinyan.assistant.service;

import com.jinyan.assistant.config.RagIngestionProperties;
import com.jinyan.assistant.entity.RagIngestionStatus;
import com.jinyan.assistant.entity.RagSegmentMessage;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RedisStreamRagSegmentQueue {

    private static final String JOB_KEY_PREFIX = "rag:ingestion:job:";
    private static final String PROCESSED_KEY_PREFIX = "rag:ingestion:processed:";
    private static final String FAILED_KEY_PREFIX = "rag:ingestion:failed:";

    private final RedissonClient redissonClient;
    private final RagIngestionProperties properties;
    private final RStream<String, String> stream;

    public RedisStreamRagSegmentQueue(
            RedissonClient redissonClient,
            RagIngestionProperties properties
    ) {
        this.redissonClient = redissonClient;
        this.properties = properties;
        this.stream = redissonClient.getStream(properties.getStreamKey(), StringCodec.INSTANCE);
    }

    public void ensureConsumerGroup() {
        try {
            stream.createGroup(properties.getConsumerGroup(), StreamMessageId.NEWEST);
        } catch (RedisBusyException ignored) {
            // The group was already created by this or another application instance.
        }
    }

    public void enqueue(String fileId, List<RagSegmentMessage> segments) {
        ensureConsumerGroup();
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

    public List<StreamEntry> readNew(String consumerName) {
        Map<StreamMessageId, Map<String, String>> messages = stream.readGroup(
                properties.getConsumerGroup(),
                consumerName,
                StreamReadGroupArgs.neverDelivered()
                        .count(1)
                        .timeout(properties.getReadTimeout())
        );
        return toEntries(messages);
    }

    public Optional<StreamEntry> claimStale(String consumerName) {
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

    public void retry(StreamEntry entry) {
        stream.add(StreamAddArgs.entries(entry.message().nextRetry().toMap()));
        acknowledge(entry.id());
    }

    public void moveToDeadLetter(StreamEntry entry, Exception exception) {
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

    public void acknowledge(StreamMessageId id) {
        stream.ack(properties.getConsumerGroup(), id);
        stream.remove(id);
    }

    public void markProcessing(String fileId) {
        RMap<String, String> job = job(fileId);
        String currentStatus = job.get("status");
        if (!isFinalStatus(currentStatus)) {
            job.put("status", "PROCESSING");
        }
    }

    public void markCompleted(RagSegmentMessage message) {
        if (!processedSegments(message.fileId()).add(message.segmentId())) {
            return;
        }
        if (failedSegments(message.fileId()).remove(message.segmentId())) {
            failedCounter(message.fileId()).decrementAndGet();
        }
        processedCounter(message.fileId()).incrementAndGet();
        updateFinalStatus(message.fileId());
    }

    public boolean isSegmentFinished(RagSegmentMessage message) {
        return processedSegments(message.fileId()).contains(message.segmentId())
                || failedSegments(message.fileId()).contains(message.segmentId());
    }

    public RagIngestionStatus getStatus(String fileId) {
        RMap<String, String> job = job(fileId);
        String status = job.get("status");
        if (status == null) {
            throw new IllegalArgumentException("RAG ingestion job not found: " + fileId);
        }
        return new RagIngestionStatus(
                fileId,
                status,
                Long.parseLong(job.getOrDefault("totalSegments", "0")),
                processedCounter(fileId).get(),
                failedCounter(fileId).get()
        );
    }

    private void markFailed(RagSegmentMessage message) {
        if (processedSegments(message.fileId()).contains(message.segmentId())
                || !failedSegments(message.fileId()).add(message.segmentId())) {
            return;
        }
        failedCounter(message.fileId()).incrementAndGet();
        updateFinalStatus(message.fileId());
    }

    private void updateFinalStatus(String fileId) {
        RMap<String, String> job = job(fileId);
        long total = Long.parseLong(job.getOrDefault("totalSegments", "0"));
        long processed = processedCounter(fileId).get();
        long failed = failedCounter(fileId).get();
        if (processed + failed < total) {
            return;
        }
        if (failed == 0) {
            job.put("status", "COMPLETED");
        } else if (processed == 0) {
            job.put("status", "FAILED");
        } else {
            job.put("status", "PARTIALLY_FAILED");
        }
    }

    private List<StreamEntry> toEntries(Map<StreamMessageId, Map<String, String>> messages) {
        List<StreamEntry> entries = new ArrayList<>(messages.size());
        messages.forEach((id, values) ->
                entries.add(new StreamEntry(id, RagSegmentMessage.fromMap(values))));
        return entries;
    }

    private RMap<String, String> job(String fileId) {
        return redissonClient.getMap(JOB_KEY_PREFIX + fileId, StringCodec.INSTANCE);
    }

    private RAtomicLong processedCounter(String fileId) {
        return redissonClient.getAtomicLong(JOB_KEY_PREFIX + fileId + ":processed-count");
    }

    private RAtomicLong failedCounter(String fileId) {
        return redissonClient.getAtomicLong(JOB_KEY_PREFIX + fileId + ":failed-count");
    }

    private RSet<String> processedSegments(String fileId) {
        return redissonClient.getSet(PROCESSED_KEY_PREFIX + fileId, StringCodec.INSTANCE);
    }

    private RSet<String> failedSegments(String fileId) {
        return redissonClient.getSet(FAILED_KEY_PREFIX + fileId, StringCodec.INSTANCE);
    }

    private boolean isFinalStatus(String status) {
        return "COMPLETED".equals(status)
                || "PARTIALLY_FAILED".equals(status)
                || "FAILED".equals(status);
    }

    private String abbreviate(String value, int maxLength) {
        String safeValue = value == null ? "Unknown processing error" : value;
        return safeValue.length() <= maxLength
                ? safeValue
                : safeValue.substring(0, maxLength);
    }

    public record StreamEntry(StreamMessageId id, RagSegmentMessage message) {
    }
}
