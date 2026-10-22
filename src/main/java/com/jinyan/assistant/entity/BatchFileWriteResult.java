package com.jinyan.assistant.entity;

import java.util.List;

/**
 * 批量文件写入汇总结果。
 *
 * @param totalCount 请求记录总数
 * @param successCount 成功提交记录数
 * @param failedCount 失败记录数
 * @param batchCount 实际拆分批次数
 * @param durationMillis 服务端处理耗时
 * @param items 按原请求顺序排列的逐条结果
 */
public record BatchFileWriteResult(
        int totalCount,
        int successCount,
        int failedCount,
        int batchCount,
        long durationMillis,
        List<BatchFileItemResult> items
) {

    /**
     * 判断是否全部写入成功。
     *
     * @return 没有失败记录时返回 {@code true}
     */
    public boolean completed() {
        return failedCount == 0;
    }
}
