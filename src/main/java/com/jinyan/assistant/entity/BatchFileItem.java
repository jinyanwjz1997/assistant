package com.jinyan.assistant.entity;

/**
 * 单条待批量写入的文件数据。
 *
 * @param fileName 文件名，最大 256 个字符
 * @param fileContext 文件正文，允许空字符串但不能为 {@code null}
 */
public record BatchFileItem(String fileName, String fileContext) {
}
