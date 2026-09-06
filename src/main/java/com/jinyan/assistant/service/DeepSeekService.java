package com.jinyan.assistant.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface DeepSeekService {

    String chat(String context);

    void streamChat(String context, SseEmitter emitter);
}
