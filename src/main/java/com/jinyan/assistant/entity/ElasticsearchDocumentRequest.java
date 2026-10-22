package com.jinyan.assistant.entity;

import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 文档写入请求。
 *
 * @param title 文档标题，必填，最多 256 个字符
 * @param content 文档正文，必填
 * @param category 业务分类，可选
 * @param tags 业务标签，可选
 * @param metadata 扩展元数据，可选
 */
public record ElasticsearchDocumentRequest(
        String title,
        String content,
        String category,
        List<String> tags,
        Map<String, Object> metadata
) {
}
