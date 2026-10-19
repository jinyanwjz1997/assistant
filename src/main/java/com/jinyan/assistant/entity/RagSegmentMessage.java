package com.jinyan.assistant.entity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redis Stream 中的单个文档片段。
 *
 * <p>只使用字符串字段，避免生产者和消费者使用不同序列化器时出现兼容问题。
 *
 * @param segmentId 子片段稳定 ID，同时作为向量存储的幂等键
 * @param fileId 子片段所属文件 ID
 * @param source 文档来源路径或来源标识
 * @param contentType 文档 MIME 类型
 * @param segmentIndex 子片段序号，从 0 开始
 * @param segmentCount 当前文档的子片段总数
 * @param text 用于摘要和向量化的子片段文本
 * @param parentSectionId 子片段所属父章节的稳定 ID
 * @param parentText 命中后用于扩展上下文的父章节文本
 * @param heading 当前章节标题
 * @param headingPath 从顶级标题到当前标题的完整路径
 * @param pageNumber 子片段所在的原文页码
 * @param previousSegmentId 前一个子片段 ID，不存在时为空字符串
 * @param nextSegmentId 后一个子片段 ID，不存在时为空字符串
 * @param retryCount 当前消息已重试次数
 */
public record RagSegmentMessage(
        String segmentId,
        String fileId,
        String source,
        String contentType,
        int segmentIndex,
        int segmentCount,
        String text,
        String parentSectionId,
        String parentText,
        String heading,
        String headingPath,
        int pageNumber,
        String previousSegmentId,
        String nextSegmentId,
        int retryCount
) {

    /**
     * 保留旧调用方式，便于普通文本消息或已有测试继续构造最小消息。
     *
     * @param segmentId 子片段稳定 ID
     * @param fileId 子片段所属文件 ID
     * @param source 文档来源
     * @param contentType 文档 MIME 类型
     * @param segmentIndex 子片段序号
     * @param segmentCount 子片段总数
     * @param text 子片段文本，同时作为默认父章节文本
     * @param retryCount 当前消息已重试次数
     */
    public RagSegmentMessage(
            String segmentId,
            String fileId,
            String source,
            String contentType,
            int segmentIndex,
            int segmentCount,
            String text,
            int retryCount
    ) {
        this(
                segmentId,
                fileId,
                source,
                contentType,
                segmentIndex,
                segmentCount,
                text,
                "",
                text,
                "",
                "",
                1,
                "",
                "",
                retryCount
        );
    }

    /**
     * 将消息转换成 Redis Stream 可直接写入的字符串字段映射。
     *
     * @return 保持字段顺序的消息映射
     */
    public Map<String, String> toMap() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("segmentId", segmentId);
        values.put("fileId", fileId);
        values.put("source", source);
        values.put("contentType", contentType);
        values.put("segmentIndex", Integer.toString(segmentIndex));
        values.put("segmentCount", Integer.toString(segmentCount));
        values.put("text", text);
        values.put("parentSectionId", safe(parentSectionId));
        values.put("parentText", safe(parentText));
        values.put("heading", safe(heading));
        values.put("headingPath", safe(headingPath));
        values.put("pageNumber", Integer.toString(pageNumber));
        values.put("previousSegmentId", safe(previousSegmentId));
        values.put("nextSegmentId", safe(nextSegmentId));
        values.put("retryCount", Integer.toString(retryCount));
        return values;
    }

    /**
     * 从 Redis Stream 字段映射恢复片段消息。
     *
     * @param values Redis Stream 消息字段
     * @return 恢复后的 RAG 片段消息
     * @throws IllegalArgumentException 当必填字段缺失时抛出
     * @throws NumberFormatException 当整数字段格式错误时抛出
     */
    public static RagSegmentMessage fromMap(Map<String, String> values) {
        return new RagSegmentMessage(
                required(values, "segmentId"),
                required(values, "fileId"),
                values.getOrDefault("source", ""),
                values.getOrDefault("contentType", ""),
                parseInt(values, "segmentIndex"),
                parseInt(values, "segmentCount"),
                required(values, "text"),
                values.getOrDefault("parentSectionId", ""),
                values.getOrDefault("parentText", values.getOrDefault("text", "")),
                values.getOrDefault("heading", ""),
                values.getOrDefault("headingPath", ""),
                parseInt(values, "pageNumber", 1),
                values.getOrDefault("previousSegmentId", ""),
                values.getOrDefault("nextSegmentId", ""),
                parseInt(values, "retryCount")
        );
    }

    /**
     * 创建下一次重试使用的消息，保留业务 ID 和全部文档元数据。
     *
     * @return 重试次数增加 1 的新消息
     */
    public RagSegmentMessage nextRetry() {
        // 重试时保持 segmentId 不变，确保向量写入仍然具备幂等性。
        return new RagSegmentMessage(
                segmentId,
                fileId,
                source,
                contentType,
                segmentIndex,
                segmentCount,
                text,
                parentSectionId,
                parentText,
                heading,
                headingPath,
                pageNumber,
                previousSegmentId,
                nextSegmentId,
                retryCount + 1
        );
    }

    /**
     * 读取必填字符串字段。
     *
     * @param values 消息字段映射
     * @param key 字段名
     * @return 字段值
     * @throws IllegalArgumentException 当字段不存在时抛出
     */
    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing Redis Stream field: " + key);
        }
        return value;
    }

    /**
     * 读取并解析必填整数字段。
     *
     * @param values 消息字段映射
     * @param key 字段名
     * @return 解析后的整数
     * @throws IllegalArgumentException 当字段不存在时抛出
     * @throws NumberFormatException 当字段不是合法整数时抛出
     */
    private static int parseInt(Map<String, String> values, String key) {
        return Integer.parseInt(required(values, key));
    }

    /**
     * 读取可选整数字段，不存在时使用默认值。
     *
     * @param values 消息字段映射
     * @param key 字段名
     * @param defaultValue 字段不存在时使用的默认值
     * @return 解析后的整数或默认值
     * @throws NumberFormatException 当已有字段不是合法整数时抛出
     */
    private static int parseInt(Map<String, String> values, String key, int defaultValue) {
        String value = values.get(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    /**
     * 将可能为空的值转换为 Redis 可保存的非空字符串。
     *
     * @param value 原始字段值
     * @return 原值，或在原值为 {@code null} 时返回空字符串
     */
    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
