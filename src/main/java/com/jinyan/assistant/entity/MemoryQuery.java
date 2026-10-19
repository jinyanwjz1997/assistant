package com.jinyan.assistant.entity;

/** 直接查询指定会话长期记忆的请求。 */
public record MemoryQuery(
        String conversationId,
        String query,
        Integer maxResults
) {

    /**
     * 返回带默认值的最大结果数。
     *
     * @return 最大结果数
     */
    public int resolvedMaxResults() {
        return maxResults == null ? 5 : maxResults;
    }
}
