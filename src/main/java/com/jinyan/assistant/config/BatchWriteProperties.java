package com.jinyan.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 批量文件数据并行写入数据库的容量和并发配置。 */
@Component
@ConfigurationProperties(prefix = "assistant.database.batch-write")
public class BatchWriteProperties {

    /** 每个独立数据库事务包含的最大记录数。 */
    private int batchSize = 100;

    /** 数据库写入线程池的固定线程数。 */
    private int parallelism = 4;

    /** 等待执行的批次数上限，队列满时由请求线程执行以形成背压。 */
    private int queueCapacity = 100;

    /** 单次 HTTP 请求允许写入的最大记录数。 */
    private int maxRecords = 10_000;

    /**
     * 获取单事务批次大小。
     *
     * @return 每批最大记录数
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * 设置单事务批次大小。
     *
     * @param batchSize 每批最大记录数
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    /**
     * 获取写入并行度。
     *
     * @return 固定线程数
     */
    public int getParallelism() {
        return parallelism;
    }

    /**
     * 设置写入并行度。
     *
     * @param parallelism 固定线程数
     */
    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }

    /**
     * 获取线程池队列容量。
     *
     * @return 队列中的最大等待批次数
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /**
     * 设置线程池队列容量。
     *
     * @param queueCapacity 最大等待批次数
     */
    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    /**
     * 获取单请求最大记录数。
     *
     * @return 最大记录数
     */
    public int getMaxRecords() {
        return maxRecords;
    }

    /**
     * 设置单请求最大记录数。
     *
     * @param maxRecords 最大记录数
     */
    public void setMaxRecords(int maxRecords) {
        this.maxRecords = maxRecords;
    }

    /** 校验配置，避免错误并行度导致线程池或数据库压力失控。 */
    public void validate() {
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalStateException("batchSize must be between 1 and 1000");
        }
        if (parallelism < 1 || parallelism > 32) {
            throw new IllegalStateException("parallelism must be between 1 and 32");
        }
        if (queueCapacity < 1 || queueCapacity > 10_000) {
            throw new IllegalStateException("queueCapacity must be between 1 and 10000");
        }
        if (maxRecords < batchSize || maxRecords > 100_000) {
            throw new IllegalStateException("maxRecords must be between batchSize and 100000");
        }
    }
}
