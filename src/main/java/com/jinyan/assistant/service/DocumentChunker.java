package com.jinyan.assistant.service;

import com.jinyan.assistant.entity.DocumentChunk;
import com.jinyan.assistant.entity.DocumentPage;

import java.util.List;

/** 将解析后的文档按结构、段落、句子和滑动窗口切成父子片段。 */
public interface DocumentChunker {

    /**
     * 切分文档。
     *
     * @param fileId 文档 ID，用于生成稳定父章节 ID
     * @param pages 按页提取的文本
     * @param contentType 文档 MIME 类型
     * @return 按原文顺序排列的检索子片段
     * @throws IllegalArgumentException 当文档 ID 为空时抛出
     * @throws IllegalStateException 当切片配置不合法时抛出
     */
    List<DocumentChunk> split(String fileId, List<DocumentPage> pages, String contentType);
}
