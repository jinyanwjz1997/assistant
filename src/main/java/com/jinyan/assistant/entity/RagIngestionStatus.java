package com.jinyan.assistant.entity;

public record RagIngestionStatus(
        String fileId,
        String status,
        long totalSegments,
        long processedSegments,
        long failedSegments
) {
}
