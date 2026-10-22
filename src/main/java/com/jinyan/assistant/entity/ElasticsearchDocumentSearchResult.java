package com.jinyan.assistant.entity;

import java.util.List;

/**
 * Elasticsearch 分页查询结果。
 *
 * @param total 满足查询条件的总文档数
 * @param page 当前页码，从 0 开始
 * @param size 当前分页大小
 * @param items 当前页命中项
 */
public record ElasticsearchDocumentSearchResult(
        long total,
        int page,
        int size,
        List<ElasticsearchDocumentHit> items
) {
}
