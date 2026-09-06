package com.jinyan.assistant.entity;

/**
 * 向量检索结果 DTO（用于接口返回，避免直接序列化 langchain4j 的 EmbeddingMatch）。
 */
public record RelevantContext(double score, String text, String fileId) {
}
