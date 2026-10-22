package com.jinyan.assistant.service;

import com.jinyan.assistant.entity.RagAnswer;
import com.jinyan.assistant.entity.RagAnswerPreparation;
import com.jinyan.assistant.entity.KnowledgeMetadataFilter;

/** 使用知识库召回内容生成有出处的回答。 */
public interface RagAnswerService {

    /**
     * 检索与问题相关的知识，并让大模型基于召回证据生成回答。
     *
     * @param question 用户问题
     * @param maxResults 期望使用的最大父章节数量
     * @return 同时包含模型回答和原始召回证据的结果
     * @throws IllegalArgumentException 当问题为空或最大结果数小于 1 时抛出
     */
    RagAnswer answer(String question, int maxResults);

    /**
     * 基于文档、短期会话记忆和长期向量记忆生成回答。
     *
     * @param question 用户问题
     * @param maxResults 文档召回数量
     * @param conversationId 会话 ID，用于隔离记忆
     * @param includeMemory 是否查询和写入记忆
     * @return 带文档证据和长期记忆证据的回答
     */
    RagAnswer answer(String question, int maxResults, String conversationId, boolean includeMemory);

    /**
     * 基于经过元数据过滤的文档、短期记忆和长期记忆生成回答。
     *
     * @param question 用户问题
     * @param maxResults 文档召回数量
     * @param conversationId 会话 ID
     * @param includeMemory 是否查询和写入记忆
     * @param metadataFilter 知识库元数据过滤条件，可以为空
     * @return 带文档证据和长期记忆证据的回答
     */
    RagAnswer answer(
            String question,
            int maxResults,
            String conversationId,
            boolean includeMemory,
            KnowledgeMetadataFilter metadataFilter
    );

    /**
     * 完成向量召回、父章节去重和提示词构造，但不调用聊天模型。
     *
     * @param question 用户问题
     * @param maxResults 期望使用的最大父章节数量
     * @return 可供同步或流式模型调用的准备结果
     * @throws IllegalArgumentException 当问题为空或最大结果数小于 1 时抛出
     */
    RagAnswerPreparation prepare(String question, int maxResults);

    /**
     * 准备包含记忆上下文的模型提示词，但不调用模型。
     *
     * @param question 用户问题
     * @param maxResults 文档召回数量
     * @param conversationId 会话 ID
     * @param includeMemory 是否查询记忆
     * @return 模型提示词及其证据
     */
    RagAnswerPreparation prepare(
            String question,
            int maxResults,
            String conversationId,
            boolean includeMemory
    );

    /**
     * 准备经过元数据过滤的文档上下文及会话记忆提示词，但不调用模型。
     *
     * @param question 用户问题
     * @param maxResults 文档召回数量
     * @param conversationId 会话 ID
     * @param includeMemory 是否查询记忆
     * @param metadataFilter 知识库元数据过滤条件，可以为空
     * @return 模型提示词及其证据
     */
    RagAnswerPreparation prepare(
            String question,
            int maxResults,
            String conversationId,
            boolean includeMemory,
            KnowledgeMetadataFilter metadataFilter
    );
}
