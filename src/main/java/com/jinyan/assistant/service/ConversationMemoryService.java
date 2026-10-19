package com.jinyan.assistant.service;

import com.jinyan.assistant.entity.MemoryContext;
import com.jinyan.assistant.entity.ShortTermMemory;

import java.util.List;

/** 管理按会话隔离的短期滑动窗口和长期向量记忆。 */
public interface ConversationMemoryService {

    /**
     * 读取指定会话的短期记忆窗口。
     *
     * @param conversationId 会话 ID
     * @return 当前窗口快照；会话 ID 无效时返回空窗口
     */
    ShortTermMemory shortTermMemory(String conversationId);

    /**
     * 查询与问题相关的长期向量记忆。
     *
     * @param conversationId 会话 ID
     * @param query 当前问题
     * @param maxResults 最大结果数
     * @return 相似度排序后的长期记忆
     */
    List<MemoryContext> searchLongTermMemory(String conversationId, String query, int maxResults);

    /**
     * 保存一轮用户操作和助手答案，同时更新短期窗口。
     *
     * @param conversationId 会话 ID
     * @param question 用户问题
     * @param answer 助手完整答案
     */
    void remember(String conversationId, String question, String answer);
}
