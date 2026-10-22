package com.jinyan.assistant.entity;

import java.util.List;

/**
 * Elasticsearch 文档组合查询请求。
 *
 * @param keyword 标题或正文关键词，可选
 * @param category 精确分类，可选
 * @param tags 标签过滤，可选
 * @param page 页码，从 0 开始，可选
 * @param size 每页数量，默认 20，最大 100
 */
public record ElasticsearchDocumentSearchRequest(
        String keyword,
        String category,
        List<String> tags,
        Integer page,
        Integer size
) {

    /** 默认页码，从 0 开始。 */
    private static final int DEFAULT_PAGE = 0;

    /** 默认每页数量。 */
    private static final int DEFAULT_SIZE = 20;

    /** 最大每页数量，避免一次查询加载过多数据。 */
    private static final int MAX_SIZE = 100;

    /**
     * 获取规范化后的页码。
     *
     * @return 从 0 开始的页码
     * @throws IllegalArgumentException 当页码小于 0 时抛出
     */
    public int resolvedPage() {
        int resolved = page == null ? DEFAULT_PAGE : page;
        if (resolved < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        return resolved;
    }

    /**
     * 获取规范化后的分页大小。
     *
     * @return 每页文档数量
     * @throws IllegalArgumentException 当分页大小不在 1 到 100 之间时抛出
     */
    public int resolvedSize() {
        int resolved = size == null ? DEFAULT_SIZE : size;
        if (resolved < 1 || resolved > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        return resolved;
    }
}
