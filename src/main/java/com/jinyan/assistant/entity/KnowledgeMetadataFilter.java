package com.jinyan.assistant.entity;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 知识库向量检索允许使用的元数据过滤条件。
 *
 * <p>不同字段之间使用 AND 连接；{@code fileIds} 和 {@code contentTypes}
 * 字段内部使用 IN 语义，即匹配列表中的任意值。
 *
 * @param fileIds 允许召回的文件 ID 列表，为空时不过滤文件
 * @param contentTypes 允许召回的 MIME 类型列表，为空时不过滤文件类型
 * @param headingPathContains 标题路径需要包含的文本，为空时不过滤标题
 * @param minPageNumber 最小页码，包含边界，为空时不限制下界
 * @param maxPageNumber 最大页码，包含边界，为空时不限制上界
 */
public record KnowledgeMetadataFilter(
        List<String> fileIds,
        List<String> contentTypes,
        String headingPathContains,
        Integer minPageNumber,
        Integer maxPageNumber
) {

    /**
     * 标准化列表和文本条件，并校验页码范围。
     *
     * @throws IllegalArgumentException 当页码不是正数或最小页码大于最大页码时抛出
     */
    public KnowledgeMetadataFilter {
        fileIds = normalizeValues(fileIds);
        contentTypes = normalizeValues(contentTypes).stream()
                .map(KnowledgeMetadataFilter::normalizeContentType)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        headingPathContains = StringUtils.hasText(headingPathContains)
                ? headingPathContains.trim()
                : null;

        if (minPageNumber != null && minPageNumber < 1) {
            throw new IllegalArgumentException("minPageNumber must be positive");
        }
        if (maxPageNumber != null && maxPageNumber < 1) {
            throw new IllegalArgumentException("maxPageNumber must be positive");
        }
        if (minPageNumber != null && maxPageNumber != null
                && minPageNumber > maxPageNumber) {
            throw new IllegalArgumentException(
                    "minPageNumber must not be greater than maxPageNumber"
            );
        }
    }

    /**
     * 判断当前对象是否包含实际过滤条件。
     *
     * @return 所有条件都为空时返回 {@code true}
     */
    public boolean isEmpty() {
        return fileIds.isEmpty()
                && contentTypes.isEmpty()
                && headingPathContains == null
                && minPageNumber == null
                && maxPageNumber == null;
    }

    /**
     * 去除列表中的空值、首尾空格和重复项，并返回不可变列表。
     *
     * @param values 原始请求值
     * @return 标准化后的不可变列表
     */
    private static List<String> normalizeValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    /**
     * 按入库规则规范 MIME 类型，避免大小写或 charset 参数造成误过滤。
     *
     * @param contentType 原始 MIME 类型
     * @return 小写且不含参数的 MIME 类型
     */
    private static String normalizeContentType(String contentType) {
        int parametersStart = contentType.indexOf(';');
        String value = parametersStart >= 0
                ? contentType.substring(0, parametersStart)
                : contentType;
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
