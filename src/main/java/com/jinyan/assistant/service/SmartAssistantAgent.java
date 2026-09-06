package com.jinyan.assistant.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface SmartAssistantAgent {

    @SystemMessage({
            "you are a smart assistant helper fro deepSeek, you should memory user's name and " +
                    "the talk for history, your should use any tools you can use when you answer the true result !"
    })
    String chat(String message);
}
