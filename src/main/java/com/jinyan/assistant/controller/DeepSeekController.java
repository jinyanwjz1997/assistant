package com.jinyan.assistant.controller;

import com.jinyan.assistant.service.DeepSeekService;
import com.jinyan.assistant.service.SmartAssistantAgent;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/ds/")
public class DeepSeekController {

    private final SmartAssistantAgent agent;

    public DeepSeekController(SmartAssistantAgent agent) {
        this.agent = agent;
    }

    @Resource
    DeepSeekService deepSeekService;

    @GetMapping("ask")
    public String getAnswer(@RequestParam(value = "content") String context) {
        return deepSeekService.chat(context);
    }

    @GetMapping("streamChat")
    public SseEmitter streamChat(@RequestParam(value = "content") String context) {
        SseEmitter sseEmitter = new SseEmitter(180000L);
        deepSeekService.streamChat(context, sseEmitter);
        return sseEmitter;
    }

    @GetMapping("talkToAgent")
    public String talkToAgent(@RequestParam(value = "content") String context) {
        return agent.chat(context);
    }
}
