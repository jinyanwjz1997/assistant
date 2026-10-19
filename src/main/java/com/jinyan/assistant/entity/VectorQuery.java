package com.jinyan.assistant.entity;

/** 文本入库或知识库查询请求。 */
public record VectorQuery(
        String context,
        String sourceFile,
        int maxResult,
        String conversationId,
        Boolean includeMemory
) {

    /**
     * 保留旧接口的三参数构造方式。
     *
     * @param context 文本或问题
     * @param sourceFile 来源文件
     * @param maxResult 最大结果数
     */
    public VectorQuery(String context, String sourceFile, int maxResult) {
        this(context, sourceFile, maxResult, null, null);
    }

    /**
     * 判断是否启用会话记忆；旧三参数请求未提供该字段时保持关闭。
     *
     * @return 请求启用记忆时返回 {@code true}
     */
    public boolean memoryEnabled() {
        return Boolean.TRUE.equals(includeMemory);
    }
}
