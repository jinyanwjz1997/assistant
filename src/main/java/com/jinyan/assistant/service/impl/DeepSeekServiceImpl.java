package com.jinyan.assistant.service.impl;

import com.jinyan.assistant.config.DeepSeekConfig;
import com.jinyan.assistant.service.DeepSeekService;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class DeepSeekServiceImpl implements DeepSeekService {

    private final OpenAiChatModel chatModel;
    private final OpenAiStreamingChatModel streamingChatModel;

    public DeepSeekServiceImpl() {
        chatModel = OpenAiChatModel.builder()
                .apiKey(DeepSeekConfig.getApiKey())
                .baseUrl(DeepSeekConfig.getBaseUrl())
                .maxTokens(DeepSeekConfig.getMaxTokens())
                .modelName(DeepSeekConfig.getModelName())
                .logRequests(true)
                .logResponses(true)
                .build();
        streamingChatModel = OpenAiStreamingChatModel.builder()
                .apiKey(DeepSeekConfig.getApiKey())
                .baseUrl(DeepSeekConfig.getBaseUrl())
                .modelName(DeepSeekConfig.getModelName())
                .maxTokens(DeepSeekConfig.getMaxTokens())
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    public String chat(String context) {
        return chatModel.chat(context);
    }

    @Override
    public void streamChat(String context, SseEmitter sseEmitter) {
        streamingChatModel.chat(context, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                System.out.print(partialResponse);
                try {
                    sseEmitter.send(SseEmitter.event().data(partialResponse));
                } catch (Exception e) {
                    sseEmitter.completeWithError(e);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse chatResponse) {
                System.out.println("complete!");
                sseEmitter.complete();
            }

            @Override
            public void onError(Throwable throwable) {
                System.out.println("error:" + throwable.getMessage());
                sseEmitter.completeWithError(throwable);
            }
        });
    }
}
