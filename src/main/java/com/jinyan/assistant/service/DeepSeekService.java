package com.jinyan.assistant.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.function.Consumer;

public interface DeepSeekService {

    /**
     * 执行同步聊天。
     *
     * @param context 模型提示词
     * @return 模型完整响应
     */
    String chat(String context);

    /**
     * 将模型增量响应直接写入 SSE。
     *
     * @param context 模型提示词
     * @param emitter SSE 发送器
     */
    void streamChat(String context, SseEmitter emitter);

    /**
     * 执行流式聊天并把生命周期事件交给调用方处理。
     *
     * @param context 模型提示词
     * @param onPartial 增量文本回调
     * @param onComplete 完成回调
     * @param onError 失败回调
     */
    void streamChat(
            String context,
            Consumer<String> onPartial,
            Runnable onComplete,
            Consumer<Throwable> onError
    );
}
