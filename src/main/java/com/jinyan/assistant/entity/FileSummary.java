package com.jinyan.assistant.entity;

/**
 * 文件列表中的轻量摘要，避免列表查询返回完整文件正文。
 *
 * @param fileId 文件唯一标识
 * @param fileName 文件名
 * @param characterCount 已保存正文的字符数
 */
public record FileSummary(
        String fileId,
        String fileName,
        int characterCount
) {
}
