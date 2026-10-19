package com.jinyan.assistant.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/** 使用普通聊天模型、会话记忆和知识库检索器构建的智能助手。 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "chatLanguageModel",
        chatMemory = "chatMemory",
        contentRetriever = "contentRetriever"
)
public interface SmartAssistantAgent {

    @SystemMessage({
            "you are a smart assistant helper fro deepSeek, you should memory user's name and " +
                    "the talk for history, your should use any tools you can use when you answer the true result !"
    })
    String chat(String message);
}
