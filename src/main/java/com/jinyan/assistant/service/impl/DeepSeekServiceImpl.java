package com.jinyan.assistant.service.impl;

import com.jinyan.assistant.config.DeepSeekConfig;
import com.jinyan.assistant.service.DeepSeekService;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.function.Consumer;

/** 提供同步和 SSE 流式 DeepSeek 对话能力。 */
@Service
public class DeepSeekServiceImpl implements DeepSeekService {

    private final OpenAiChatModel chatModel;
    private final OpenAiStreamingChatModel streamingChatModel;

    /**
     * 创建 DeepSeek 对话服务。
     *
     * @param chatModel Spring 管理的普通对话模型
     * @param config DeepSeek 连接及请求配置
     */
    public DeepSeekServiceImpl(
            @Qualifier("chatLanguageModel") OpenAiChatModel chatModel,
            DeepSeekConfig config
    ) {
        this.chatModel = chatModel;
        streamingChatModel = OpenAiStreamingChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .maxTokens(config.getMaxTokens())
                .timeout(config.getTimeout())
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 执行同步对话请求。
     *
     * @param context 用户输入
     * @return 模型完整响应
     */
    @Override
    public String chat(String context) {
        return chatModel.chat(context);
    }

    /**
     * 执行流式对话，并通过 SSE 将增量内容发送给调用方。
     *
     * @param context 用户输入
     * @param sseEmitter SSE 响应发送器
     */
    @Override
    public void streamChat(String context, SseEmitter sseEmitter) {
        streamChat(
                context,
                partialResponse -> sendPartialResponse(sseEmitter, partialResponse),
                sseEmitter::complete,
                sseEmitter::completeWithError
        );
    }

    /**
     * 执行流式对话，并将增量、完成和失败事件转交给调用方。
     *
     * @param context 用户输入或完整模型提示词
     * @param onPartial 增量文本回调
     * @param onComplete 完成回调
     * @param onError 失败回调
     */
    @Override
    public void streamChat(
            String context,
            Consumer<String> onPartial,
            Runnable onComplete,
            Consumer<Throwable> onError
    ) {
        streamingChatModel.chat(context, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                onPartial.accept(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse chatResponse) {
                onComplete.run();
            }

            @Override
            public void onError(Throwable throwable) {
                onError.accept(throwable);
            }
        });
    }

    /**
     * 向通用聊天 SSE 发送一个增量文本事件。
     *
     * @param emitter SSE 发送器
     * @param partialResponse 模型增量文本
     * @throws IllegalStateException 当客户端连接已断开或发送失败时抛出
     */
    private void sendPartialResponse(SseEmitter emitter, String partialResponse) {
        try {
            emitter.send(SseEmitter.event().data(partialResponse));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to send streaming response", exception);
        }
    }
}
