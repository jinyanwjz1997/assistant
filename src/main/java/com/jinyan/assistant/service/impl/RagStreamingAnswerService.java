package com.jinyan.assistant.service.impl;

import com.jinyan.assistant.entity.RagAnswerPreparation;
import com.jinyan.assistant.entity.KnowledgeMetadataFilter;
import com.jinyan.assistant.exception.ApiErrorResponse;
import com.jinyan.assistant.service.ConversationMemoryService;
import com.jinyan.assistant.service.DeepSeekService;
import com.jinyan.assistant.service.RagAnswerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** 将 RAG 召回证据和模型增量回答转换为浏览器可消费的 SSE 事件。 */
@Service
@Slf4j
public class RagStreamingAnswerService {

    private static final long STREAM_TIMEOUT_MILLIS = 180_000L;
    private static final String EMPTY_MODEL_ANSWER =
            "大模型未能生成有效回答，请稍后重试。";

    private final RagAnswerService ragAnswerService;
    private final DeepSeekService deepSeekService;
    private final ConversationMemoryService conversationMemoryService;

    /**
     * 创建 RAG 流式回答服务。
     *
     * @param ragAnswerService RAG 召回及提示词服务
     * @param deepSeekService DeepSeek 流式模型服务
     */
    public RagStreamingAnswerService(
            RagAnswerService ragAnswerService,
            DeepSeekService deepSeekService,
            ConversationMemoryService conversationMemoryService
    ) {
        this.ragAnswerService = ragAnswerService;
        this.deepSeekService = deepSeekService;
        this.conversationMemoryService = conversationMemoryService;
    }

    /**
     * 创建 SSE 连接，依次发送 {@code sources}、{@code token} 和 {@code done} 事件。
     *
     * @param question 用户问题
     * @param maxResults 最大召回父章节数量
     * @return 已启动回答任务的 SSE 发送器
     */
    public SseEmitter streamAnswer(String question, int maxResults) {
        return streamAnswer(question, maxResults, "", false);
    }

    /**
     * 创建带会话记忆的 RAG 流式回答。
     *
     * @param question 用户问题
     * @param maxResults 文档和记忆召回数量上限
     * @param conversationId 会话 ID
     * @param includeMemory 是否启用记忆
     * @return SSE 发送器
     */
    public SseEmitter streamAnswer(
            String question,
            int maxResults,
            String conversationId,
            boolean includeMemory
    ) {
        return streamAnswer(question, maxResults, conversationId, includeMemory, null);
    }

    /**
     * 创建经过知识库元数据过滤且带会话记忆的 RAG 流式回答。
     *
     * @param question 用户问题
     * @param maxResults 文档和记忆召回数量上限
     * @param conversationId 会话 ID
     * @param includeMemory 是否启用记忆
     * @param metadataFilter 知识库元数据过滤条件，可以为空
     * @return SSE 发送器
     */
    public SseEmitter streamAnswer(
            String question,
            int maxResults,
            String conversationId,
            boolean includeMemory,
            KnowledgeMetadataFilter metadataFilter
    ) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicBoolean failed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));

        try {
            RagAnswerPreparation preparation = ragAnswerService.prepare(
                    question,
                    maxResults,
                    conversationId,
                    includeMemory,
                    metadataFilter
            );
            sendEvent(
                    emitter,
                    "conversation",
                    Map.of("conversationId", preparation.conversationId()),
                    MediaType.APPLICATION_JSON
            );
            sendEvent(emitter, "sources", preparation.contexts(), MediaType.APPLICATION_JSON);
            sendEvent(emitter, "memories", preparation.memories(), MediaType.APPLICATION_JSON);

            if (preparation.contexts().isEmpty()
                    && preparation.memories().isEmpty()
                    && preparation.prompt().isEmpty()) {
                String fallback = RagAnswerServiceImpl.NO_RELEVANT_CONTEXT_ANSWER;
                sendJsonEvent(
                        emitter,
                        "token",
                        Map.of("content", fallback)
                );
                remember(preparation, fallback, includeMemory);
                sendEvent(emitter, "done", Map.of("completed", true), MediaType.APPLICATION_JSON);
                closed.set(true);
                emitter.complete();
                return emitter;
            }

            StringBuilder completeAnswer = new StringBuilder();
            CitationNormalizingConsumer normalizer = new CitationNormalizingConsumer(
                    RagAnswerServiceImpl.citationFallback(preparation.contexts()),
                    memoryCitationFallback(preparation),
                    partial -> {
                        completeAnswer.append(partial);
                        sendJsonEvent(emitter, "token", Map.of("content", partial));
                    }
            );
            deepSeekService.streamChat(
                    preparation.prompt(),
                    partial -> {
                        if (!closed.get()) {
                            normalizer.accept(partial);
                        }
                    },
                    () -> {
                        if (closed.get() || failed.get()) {
                            return;
                        }
                        normalizer.flush();
                        if (!StringUtils.hasText(completeAnswer)) {
                            completeAnswer.append(EMPTY_MODEL_ANSWER);
                            sendJsonEvent(
                                    emitter,
                                    "token",
                                    Map.of("content", EMPTY_MODEL_ANSWER)
                            );
                        }
                        if (closed.get() || failed.get()) {
                            return;
                        }
                        remember(preparation, completeAnswer.toString(), includeMemory);
                        complete(emitter, closed);
                    },
                    throwable -> {
                        failed.set(true);
                        fail(emitter, closed, throwable);
                    }
            );
        } catch (Exception exception) {
            fail(emitter, closed, exception);
        }
        return emitter;
    }

    /**
     * 获取流式长期记忆引用兜底文本。
     *
     * @param preparation RAG 准备结果
     * @return 例如 {@code [记忆1][记忆2]}
     */
    private String memoryCitationFallback(RagAnswerPreparation preparation) {
        StringBuilder fallback = new StringBuilder();
        preparation.memories().forEach(memory ->
                fallback.append("[记忆").append(memory.memoryNumber()).append(']'));
        return fallback.isEmpty() ? "[无可用记忆]" : fallback.toString();
    }

    /**
     * 保存流式回答完成后的完整文本。
     *
     * @param preparation RAG 准备结果
     * @param answer 完整回答
     * @param includeMemory 是否启用记忆
     */
    private void remember(RagAnswerPreparation preparation, String answer, boolean includeMemory) {
        if (!includeMemory || !StringUtils.hasText(preparation.conversationId())
                || answer == null) {
            return;
        }
        try {
            conversationMemoryService.remember(
                    preparation.conversationId(),
                    preparation.question(),
                    answer
            );
        } catch (RuntimeException exception) {
            log.warn("保存流式会话记忆失败, conversationId:{}", preparation.conversationId(), exception);
        }
    }

    /**
     * 发送完成事件并关闭 SSE 连接。
     *
     * @param emitter SSE 发送器
     * @param closed 连接关闭标记
     */
    private void complete(SseEmitter emitter, AtomicBoolean closed) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            sendEvent(emitter, "done", Map.of("completed", true), MediaType.APPLICATION_JSON);
            emitter.complete();
        } catch (Exception exception) {
            emitter.completeWithError(exception);
        }
    }

    /**
     * 向客户端发送安全的失败信息并关闭连接。
     *
     * @param emitter SSE 发送器
     * @param closed 连接关闭标记
     * @param throwable 原始异常
     */
    private void fail(SseEmitter emitter, AtomicBoolean closed, Throwable throwable) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        String requestId = UUID.randomUUID().toString();
        log.error("RAG流式回答失败, requestId:{}", requestId, throwable);
        try {
            sendEvent(
                    emitter,
                    "failure",
                    ApiErrorResponse.of(
                            HttpStatus.INTERNAL_SERVER_ERROR.value(),
                            "RAG_STREAM_ERROR",
                            "回答生成失败，请稍后重试。",
                            "/embedding/searchRelevantContext/stream",
                            requestId,
                            Map.of()
                    ),
                    MediaType.APPLICATION_JSON
            );
            emitter.complete();
        } catch (Exception sendException) {
            emitter.completeWithError(sendException);
        }
    }

    /**
     * 发送指定名称的 SSE 事件。
     *
     * @param emitter SSE 发送器
     * @param name 事件名称
     * @param data 事件数据
     * @param mediaType 数据媒体类型，可以为空
     * @throws IllegalStateException 当事件无法发送时抛出
     */
    private void sendEvent(
            SseEmitter emitter,
            String name,
            Object data,
            MediaType mediaType
    ) {
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event().name(name);
            event = mediaType == null ? event.data(data) : event.data(data, mediaType);
            emitter.send(event);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to send SSE event: " + name, exception);
        }
    }

    /**
     * 发送 JSON 格式的 SSE 事件，确保换行和引号不会破坏 SSE 行协议。
     *
     * @param emitter SSE 发送器
     * @param name 事件名称
     * @param data 待序列化数据
     */
    private void sendJsonEvent(SseEmitter emitter, String name, Object data) {
        sendEvent(emitter, name, data, MediaType.APPLICATION_JSON);
    }

    /**
     * 跨流式分片识别无编号的 {@code [资料]}，并替换为明确编号。
     *
     * <p>模型可能把一个引用标签拆到多个 token 中，因此始终保留可能构成标签前缀的
     * 最短尾部，直到后续分片到达或流结束时再输出。
     */
    static final class CitationNormalizingConsumer implements Consumer<String> {

        private static final String GENERIC_CITATION = "[资料]";

        private final String replacement;
        private final String memoryReplacement;
        private final Consumer<String> downstream;
        private final StringBuilder pending = new StringBuilder();

        /**
         * 创建引用标准化消费者。
         *
         * @param replacement 无编号引用的替换文本
         * @param downstream 标准化文本接收方
         */
        CitationNormalizingConsumer(String replacement, Consumer<String> downstream) {
            this(replacement, "[无可用记忆]", downstream);
        }

        /**
         * 创建同时处理文档和记忆引用的消费者。
         *
         * @param replacement 文档无编号引用替换文本
         * @param memoryReplacement 记忆无编号引用替换文本
         * @param downstream 标准化文本接收方
         */
        CitationNormalizingConsumer(
                String replacement,
                String memoryReplacement,
                Consumer<String> downstream
        ) {
            this.replacement = replacement;
            this.memoryReplacement = memoryReplacement;
            this.downstream = downstream;
        }

        /**
         * 接收一个模型增量分片，并输出当前可以安全判断的内容。
         *
         * @param partialResponse 模型增量文本
         */
        @Override
        public void accept(String partialResponse) {
            if (partialResponse == null || partialResponse.isEmpty()) {
                return;
            }
            pending.append(partialResponse);
            emitResolved(false);
        }

        /** 将流结束时尚未输出的尾部全部发送给下游。 */
        void flush() {
            emitResolved(true);
        }

        /**
         * 输出已经能够确定不属于跨分片标签的文本。
         *
         * @param flushAll 是否强制输出全部尾部
         */
        private void emitResolved(boolean flushAll) {
            int retainedLength = flushAll ? 0 : possiblePrefixSuffixLength(pending);
            int resolvedLength = pending.length() - retainedLength;
            if (resolvedLength == 0) {
                return;
            }
            String resolved = pending.substring(0, resolvedLength)
                    .replace(GENERIC_CITATION, replacement)
                    .replace("[记忆]", memoryReplacement);
            pending.delete(0, resolvedLength);
            if (!resolved.isEmpty()) {
                downstream.accept(resolved);
            }
        }

        /**
         * 计算当前文本尾部与 {@code [资料]} 前缀重合的最大长度。
         *
         * @param value 尚未输出的文本
         * @return 需要保留等待下一分片的字符数
         */
        private int possiblePrefixSuffixLength(CharSequence value) {
            int maximum = Math.min(
                    value.length(),
                    Math.max(GENERIC_CITATION.length(), "[记忆]".length()) - 1
            );
            for (int length = maximum; length > 0; length--) {
                if (hasPrefixSuffix(value, GENERIC_CITATION, length)
                        || hasPrefixSuffix(value, "[记忆]", length)) {
                    return length;
                }
            }
            return 0;
        }

        /**
         * 判断文本尾部是否是指定标签的前缀。
         *
         * @param value 待判断文本
         * @param pattern 完整标签
         * @param length 前缀长度
         * @return 匹配时返回 {@code true}
         */
        private boolean hasPrefixSuffix(CharSequence value, String pattern, int length) {
            if (length > pattern.length() - 1) {
                return false;
            }
            int offset = value.length() - length;
            for (int index = 0; index < length; index++) {
                if (value.charAt(offset + index) != pattern.charAt(index)) {
                    return false;
                }
            }
            return true;
        }
    }
}
