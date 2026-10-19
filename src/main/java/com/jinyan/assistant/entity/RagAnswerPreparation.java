package com.jinyan.assistant.entity;

import java.util.List;

/**
 * RAG 回答生成前的召回结果。
 *
 * @param question 用户原始问题
 * @param prompt 已包含安全规则和编号资料的模型提示词；无召回资料时为空
 * @param contexts 去重后的原始召回证据
 * @param memories 本次回答使用的长期记忆
 * @param conversationId 当前会话 ID
 */
public record RagAnswerPreparation(
        String question,
        String prompt,
        List<RelevantContext> contexts,
        List<MemoryContext> memories,
        String conversationId
) {

    /**
     * 保留不带记忆参数的旧构造方式。
     *
     * @param question 用户问题
     * @param prompt 模型提示词
     * @param contexts 文档召回证据
     */
    public RagAnswerPreparation(String question, String prompt, List<RelevantContext> contexts) {
        this(question, prompt, contexts, List.of(), "");
    }
}
