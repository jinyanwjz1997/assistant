package com.jinyan.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 文档切片参数。
 *
 * <p>当前按字符数控制大小，因为项目使用的 Qwen Embedding 模型没有接入与其完全一致的
 * Tokenizer。将参数集中配置后，可以根据真实检索评测结果调整，而不需要修改代码。
 */
@Component
@ConfigurationProperties(prefix = "assistant.rag.chunking")
public class RagChunkingProperties {

    /** 用于向量检索的子片段最大字符数。 */
    private int childMaxCharacters = 600;
    /** 相邻子片段重复保留的字符数，用来保护切片边界上下文。 */
    private int childOverlapCharacters = 100;
    /** 尽量合并小片段时使用的最小字符数。 */
    private int minChildCharacters = 120;
    /** 命中子片段后返回给调用方的父章节最大字符数。 */
    private int parentMaxCharacters = 2400;

    /**
     * 获取子片段最大字符数。
     *
     * @return 子片段最大字符数
     */
    public int getChildMaxCharacters() {
        return childMaxCharacters;
    }

    /**
     * 设置子片段最大字符数。
     *
     * @param childMaxCharacters 子片段最大字符数
     */
    public void setChildMaxCharacters(int childMaxCharacters) {
        this.childMaxCharacters = childMaxCharacters;
    }

    /**
     * 获取相邻子片段的重叠字符数。
     *
     * @return 重叠字符数
     */
    public int getChildOverlapCharacters() {
        return childOverlapCharacters;
    }

    /**
     * 设置相邻子片段的重叠字符数。
     *
     * @param childOverlapCharacters 重叠字符数
     */
    public void setChildOverlapCharacters(int childOverlapCharacters) {
        this.childOverlapCharacters = childOverlapCharacters;
    }

    /**
     * 获取子片段期望达到的最小字符数。
     *
     * @return 子片段最小字符数
     */
    public int getMinChildCharacters() {
        return minChildCharacters;
    }

    /**
     * 设置子片段期望达到的最小字符数。
     *
     * @param minChildCharacters 子片段最小字符数
     */
    public void setMinChildCharacters(int minChildCharacters) {
        this.minChildCharacters = minChildCharacters;
    }

    /**
     * 获取父章节最大字符数。
     *
     * @return 父章节最大字符数
     */
    public int getParentMaxCharacters() {
        return parentMaxCharacters;
    }

    /**
     * 设置父章节最大字符数。
     *
     * @param parentMaxCharacters 父章节最大字符数
     */
    public void setParentMaxCharacters(int parentMaxCharacters) {
        this.parentMaxCharacters = parentMaxCharacters;
    }
}
