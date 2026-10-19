package com.jinyan.assistant.controller;

import com.jinyan.assistant.entity.RagAnswer;
import com.jinyan.assistant.entity.RagIngestionStatus;
import com.jinyan.assistant.entity.MemoryContext;
import com.jinyan.assistant.entity.MemoryQuery;
import com.jinyan.assistant.entity.VectorQuery;
import com.jinyan.assistant.service.ConversationMemoryService;
import com.jinyan.assistant.service.RagAnswerService;
import com.jinyan.assistant.service.RedisStreamRagSegmentQueue;
import com.jinyan.assistant.service.VectorService;
import com.jinyan.assistant.service.impl.RagStreamingAnswerService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** 提供知识入库、文档上传、向量检索和异步入库进度查询接口。 */
@RestController
@RequestMapping("/embedding")
public class PgVectorController {

    @Resource
    private VectorService vectorService;

    @Resource
    private RagAnswerService ragAnswerService;

    @Resource
    private RagStreamingAnswerService ragStreamingAnswerService;

    @Resource
    private ConversationMemoryService conversationMemoryService;

    @Resource
    private RedisStreamRagSegmentQueue ragSegmentQueue;

    /**
     * 将请求中的普通文本保存并发送到 RAG 异步处理队列。
     *
     * @param vectorQuery 包含文本内容和来源信息的请求
     * @return 入队成功标识
     */
    @PostMapping("/addKnowledge")
    public String addKnowledge(@RequestBody VectorQuery vectorQuery) {
        vectorService.addTextToVector(vectorQuery.context(), vectorQuery.sourceFile());
        return "success";
    }

    /**
     * 检索与问题相关的知识，并让大模型基于召回证据生成带引用的回答。
     *
     * @param vectorQuery 包含查询文本和最大结果数的请求
     * @return 大模型回答及其使用的原始召回证据
     * @throws IllegalArgumentException 当查询文本为空或最大结果数小于 1 时抛出
     */
    @PostMapping("/searchRelevantContext")
    public RagAnswer searchRelevantContext(@RequestBody VectorQuery vectorQuery) {
        return ragAnswerService.answer(
                vectorQuery.context(),
                vectorQuery.maxResult(),
                vectorQuery.conversationId(),
                vectorQuery.memoryEnabled()
        );
    }

    /**
     * 直接查询某个会话的长期向量记忆。
     *
     * @param memoryQuery 会话 ID、查询文本和最大结果数
     * @return 相似度排序后的历史问答记忆
     */
    @PostMapping("/searchMemory")
    public List<MemoryContext> searchMemory(@RequestBody MemoryQuery memoryQuery) {
        return conversationMemoryService.searchLongTermMemory(
                memoryQuery.conversationId(),
                memoryQuery.query(),
                memoryQuery.resolvedMaxResults()
        );
    }

    /**
     * 以 SSE 方式返回知识库引用和大模型增量回答。
     *
     * @param question 用户问题
     * @param maxResults 最大召回父章节数量
     * @return RAG 流式响应发送器
     */
    @GetMapping(value = "/searchRelevantContext/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRelevantContext(
            @RequestParam String question,
            @RequestParam(defaultValue = "5") int maxResults,
            @RequestParam(required = false) String conversationId,
            @RequestParam(defaultValue = "true") boolean includeMemory
    ) {
        return ragStreamingAnswerService.streamAnswer(
                question,
                maxResults,
                conversationId,
                includeMemory
        );
    }

    /**
     * 读取服务器本地文档并启动异步 RAG 入库。
     *
     * @param filePath 服务器可访问的本地文件路径
     * @return 文档文件 ID
     */
    @PostMapping("/readDocument")
    public String readDocument(@RequestParam String filePath) {
        return vectorService.readDocument(filePath);
    }

    /**
     * 接收上传文档并启动异步 RAG 入库。
     *
     * @param file 待上传的文档
     * @return 文档文件 ID
     * @throws IOException 当无法打开上传文件输入流时抛出
     * @throws IllegalArgumentException 当上传文件为空时抛出
     */
    @PostMapping(value = "/uploadDocument", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadDocument(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("file must not be empty");
        }

        try (InputStream inputStream = file.getInputStream()) {
            return vectorService.readDocument(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    inputStream
            );
        }
    }

    /**
     * 查询文档异步摘要及向量入库的当前进度。
     *
     * @param fileId 文档文件 ID
     * @return 当前文档的异步入库状态
     */
    @GetMapping("/ingestion/{fileId}")
    public RagIngestionStatus ingestionStatus(@PathVariable String fileId) {
        return ragSegmentQueue.getStatus(fileId);
    }
}
