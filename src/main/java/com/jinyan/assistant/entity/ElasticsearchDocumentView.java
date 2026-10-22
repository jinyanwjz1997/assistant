package com.jinyan.assistant.entity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 对外返回的 Elasticsearch 文档视图。 */
public record ElasticsearchDocumentView(
        String id,
        String title,
        String content,
        String category,
        List<String> tags,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * 将索引文档转换为接口视图。
     *
     * @param document Elasticsearch 文档
     * @return 文档接口视图
     */
    public static ElasticsearchDocumentView from(ElasticsearchDocument document) {
        return new ElasticsearchDocumentView(
                document.getId(),
                document.getTitle(),
                document.getContent(),
                document.getCategory(),
                document.getTags(),
                document.getMetadata(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
