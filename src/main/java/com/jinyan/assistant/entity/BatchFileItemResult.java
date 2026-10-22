package com.jinyan.assistant.entity;

/**
 * 单条批量文件数据的写入结果。
 *
 * @param index 数据在请求列表中的下标
 * @param fileId 成功写入后的文件 ID，失败时为空
 * @param fileName 文件名
 * @param success 是否成功提交
 * @param errorCode 失败错误码，成功时为空
 * @param message 结果说明
 */
public record BatchFileItemResult(
        int index,
        String fileId,
        String fileName,
        boolean success,
        String errorCode,
        String message
) {
}
