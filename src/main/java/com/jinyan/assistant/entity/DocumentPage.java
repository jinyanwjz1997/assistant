package com.jinyan.assistant.entity;

/**
 * 解析后的一页文档；普通文本没有真实分页时统一使用第 1 页。
 *
 * @param pageNumber 从 1 开始的原文页码
 * @param text 当前页面提取出的文本
 */
public record DocumentPage(int pageNumber, String text) {
}
