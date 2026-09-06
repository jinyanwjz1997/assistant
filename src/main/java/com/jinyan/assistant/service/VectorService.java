package com.jinyan.assistant.service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;

import java.io.InputStream;
import java.util.List;

public interface VectorService {

    /**
     * 将文本存入vector
     *
     * @param context 文本内容
     * @param sourceFile 文本来源
     */
    void addTextToVector(String context, String sourceFile);

    /**
     * 查询向量
     *
     * @param userQuery 查询请求
     * @param maxResults top K
     * @return topK
     */
    List<EmbeddingMatch<TextSegment>> searchRelevantContext(String userQuery, int maxResults);

    /**
     * 读取文件
     *
     * @param filePath 本机文件路径
     * @return 文件ID
     */
    String readDocument(String filePath);

    /**
     * 读取上传文件并写入原文库和向量库。
     *
     * @param fileName 文件名
     * @param contentType MIME 类型
     * @param inputStream 文件内容
     * @return 文件ID
     */
    String readDocument(String fileName, String contentType, InputStream inputStream);
}
