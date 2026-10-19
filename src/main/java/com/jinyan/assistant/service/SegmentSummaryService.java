package com.jinyan.assistant.service;

/** 为一个原始文档片段生成可写入向量元数据的摘要。 */
public interface SegmentSummaryService {

    /** 使用集群级 Redis 锁调用模型生成片段摘要。
     * @param text 原始片段文本
     * @return 摘要正文
     * @throws InterruptedException 等待分布式锁时线程被中断
     */
    String summarize(String text) throws InterruptedException;
}
