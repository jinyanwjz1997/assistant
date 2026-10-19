package com.jinyan.assistant.entity;

import java.util.List;

/** 当前会话滑动窗口中的消息快照。 */
public record ShortTermMemory(
        String conversationId,
        List<String> messages
) {
}
