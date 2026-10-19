package com.jinyan.assistant.entity;

/**
 * 向量检索结果 DTO（用于接口返回，避免直接序列化 LangChain4j 的 EmbeddingMatch）。
 *
 * @param sourceNumber 本次回答中的资料编号，从 1 开始，与回答中的 {@code [资料N]} 一致
 * @param score 子片段与查询向量的相似度分数
 * @param text 命中子片段所属的完整父章节
 * @param matchedText 实际参与向量匹配的子片段
 * @param summary 子片段摘要
 * @param fileId 命中文档的文件 ID
 * @param headingPath 命中内容的完整标题路径
 * @param pageNumber 命中内容所在的原文页码
 */
public record RelevantContext(
        int sourceNumber,
        double score,
        String text,
        String matchedText,
        String summary,
        String fileId,
        String headingPath,
        Integer pageNumber
) {
}
