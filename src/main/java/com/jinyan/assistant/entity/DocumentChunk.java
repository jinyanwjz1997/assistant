package com.jinyan.assistant.entity;

/**
 * 智能切片结果。
 *
 * @param text 用于摘要、向量化和精确命中的子片段
 * @param parentText 子片段所属的较完整父章节，检索命中后返回它以减少上下文截断
 * @param parentSectionId 父章节稳定 ID，同一章节的多个子片段共享该 ID
 * @param heading 当前标题
 * @param headingPath 从上级标题到当前标题的完整路径
 * @param pageNumber 片段起始页；无真实分页信息时为 1
 */
public record DocumentChunk(
        String text,
        String parentText,
        String parentSectionId,
        String heading,
        String headingPath,
        int pageNumber
) {
}
