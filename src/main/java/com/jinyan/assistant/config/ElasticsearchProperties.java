package com.jinyan.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Elasticsearch 业务索引配置。 */
@Component
@ConfigurationProperties(prefix = "assistant.elasticsearch")
public class ElasticsearchProperties {

    /** 文档索引名称。 */
    private String indexName = "assistant_documents";

    /**
     * 获取文档索引名称。
     *
     * @return Elasticsearch 索引名称
     */
    public String getIndexName() {
        return indexName;
    }

    /**
     * 设置文档索引名称。
     *
     * @param indexName Elasticsearch 索引名称
     */
    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    /**
     * 校验索引配置。
     *
     * @throws IllegalStateException 当索引名称为空或包含空白字符时抛出
     */
    public void validate() {
        if (!StringUtils.hasText(indexName)
                || indexName.chars().anyMatch(Character::isWhitespace)
                || !indexName.equals(indexName.toLowerCase(java.util.Locale.ROOT))
                || indexName.chars().anyMatch(character -> "\\/*?\"<>|,#".indexOf(character) >= 0)) {
            throw new IllegalStateException("assistant.elasticsearch.index-name must be a non-blank name");
        }
    }
}
