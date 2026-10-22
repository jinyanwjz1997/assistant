package com.jinyan.assistant.entity;

/**
 * 带 Elasticsearch 相关性得分的搜索命中。
 *
 * @param score Elasticsearch 相关性得分
 * @param document 命中的文档
 */
public record ElasticsearchDocumentHit(
        double score,
        ElasticsearchDocumentView document
) {
}
