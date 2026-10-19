package com.jinyan.assistant.entity;

import java.time.Instant;

/**
 * 长期向量记忆检索结果。
 *
 * @param memoryNumber 本次回答中的记忆编号，从 1 开始
 * @param score 记忆与当前问题的相似度
 * @param text 已保存的用户操作和助手答案
 * @param conversationId 所属会话 ID
 * @param createdAt 记忆创建时间
 */
public record MemoryContext(
        int memoryNumber,
        double score,
        String text,
        String conversationId,
        Instant createdAt
) {
}
