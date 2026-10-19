package com.jinyan.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 短期会话记忆和长期向量记忆配置。 */
@Component
@ConfigurationProperties(prefix = "assistant.memory")
public class MemoryProperties {

    /** 短期记忆最多保留的聊天消息条数，用户消息和 AI 消息分别计数。 */
    private int shortTermMaxMessages = 10;

    /** 长期记忆检索的最大结果数。 */
    private int longTermMaxResults = 3;

    /** 长期记忆向量检索的最低相似度。 */
    private double longTermMinScore = 0.65;

    /** 是否启用长期记忆写入和查询。 */
    private boolean longTermEnabled = true;

    /**
     * 获取短期记忆窗口大小。
     *
     * @return 最大消息条数
     */
    public int getShortTermMaxMessages() {
        return shortTermMaxMessages;
    }

    /**
     * 设置短期记忆窗口大小。
     *
     * @param shortTermMaxMessages 最大消息条数
     */
    public void setShortTermMaxMessages(int shortTermMaxMessages) {
        this.shortTermMaxMessages = shortTermMaxMessages;
    }

    /**
     * 获取长期记忆检索数量。
     *
     * @return 最大结果数
     */
    public int getLongTermMaxResults() {
        return longTermMaxResults;
    }

    /**
     * 设置长期记忆检索数量。
     *
     * @param longTermMaxResults 最大结果数
     */
    public void setLongTermMaxResults(int longTermMaxResults) {
        this.longTermMaxResults = longTermMaxResults;
    }

    /**
     * 获取长期记忆最低相似度。
     *
     * @return 最低相似度
     */
    public double getLongTermMinScore() {
        return longTermMinScore;
    }

    /**
     * 设置长期记忆最低相似度。
     *
     * @param longTermMinScore 最低相似度
     */
    public void setLongTermMinScore(double longTermMinScore) {
        this.longTermMinScore = longTermMinScore;
    }

    /**
     * 判断是否启用长期记忆。
     *
     * @return 启用时返回 {@code true}
     */
    public boolean isLongTermEnabled() {
        return longTermEnabled;
    }

    /**
     * 设置是否启用长期记忆。
     *
     * @param longTermEnabled 是否启用
     */
    public void setLongTermEnabled(boolean longTermEnabled) {
        this.longTermEnabled = longTermEnabled;
    }
}
