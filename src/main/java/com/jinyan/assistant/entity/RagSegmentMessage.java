package com.jinyan.assistant.entity;

import java.util.LinkedHashMap;
import java.util.Map;

public record RagSegmentMessage(
        String segmentId,
        String fileId,
        String source,
        String contentType,
        int segmentIndex,
        int segmentCount,
        String text,
        int retryCount
) {

    public Map<String, String> toMap() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("segmentId", segmentId);
        values.put("fileId", fileId);
        values.put("source", source);
        values.put("contentType", contentType);
        values.put("segmentIndex", Integer.toString(segmentIndex));
        values.put("segmentCount", Integer.toString(segmentCount));
        values.put("text", text);
        values.put("retryCount", Integer.toString(retryCount));
        return values;
    }

    public static RagSegmentMessage fromMap(Map<String, String> values) {
        return new RagSegmentMessage(
                required(values, "segmentId"),
                required(values, "fileId"),
                values.getOrDefault("source", ""),
                values.getOrDefault("contentType", ""),
                parseInt(values, "segmentIndex"),
                parseInt(values, "segmentCount"),
                required(values, "text"),
                parseInt(values, "retryCount")
        );
    }

    public RagSegmentMessage nextRetry() {
        return new RagSegmentMessage(
                segmentId,
                fileId,
                source,
                contentType,
                segmentIndex,
                segmentCount,
                text,
                retryCount + 1
        );
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing Redis Stream field: " + key);
        }
        return value;
    }

    private static int parseInt(Map<String, String> values, String key) {
        return Integer.parseInt(required(values, key));
    }
}
