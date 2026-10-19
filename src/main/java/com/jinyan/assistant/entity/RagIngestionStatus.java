package com.jinyan.assistant.entity;

/** 上传接口返回 fileId 后，可通过该模型查询异步入库进度。 */
public record RagIngestionStatus(
        String fileId,
        String status,
        long totalSegments,
        long processedSegments,
        long failedSegments
) {
}
