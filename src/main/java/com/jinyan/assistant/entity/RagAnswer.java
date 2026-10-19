package com.jinyan.assistant.entity;

import java.util.List;

/**
 * RAG 问答结果。
 *
 * @param question 用户原始问题
 * @param answer 大模型根据召回资料生成的回答
 * @param conversationId 当前会话 ID
 * @param contexts 本次回答使用的原始召回证据，顺序与回答中的资料编号一致
 * @param memories 本次回答使用的长期记忆，顺序与回答中的记忆编号一致
 */
public record RagAnswer(
        String question,
        String answer,
        String conversationId,
        List<RelevantContext> contexts,
        List<MemoryContext> memories
) {

    /**
     * 保留不带记忆参数的旧构造方式。
     *
     * @param question 用户问题
     * @param answer 回答内容
     * @param contexts 文档召回证据
     */
    public RagAnswer(String question, String answer, List<RelevantContext> contexts) {
        this(question, answer, "", contexts, List.of());
    }
}
