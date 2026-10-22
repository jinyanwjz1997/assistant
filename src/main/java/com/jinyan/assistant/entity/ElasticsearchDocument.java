package com.jinyan.assistant.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 中保存的业务文档。
 *
 * <p>该类只描述索引字段，不承担请求参数校验和业务逻辑。标题、正文使用全文字段，
 * 分类和标签使用 Keyword 字段，便于精确过滤。
 */
@Document(indexName = "assistant_documents", createIndex = false)
public class ElasticsearchDocument {

    /** Elasticsearch 文档 ID。 */
    @Id
    private String id;

    /** 文档标题，支持全文检索。 */
    @Field(type = FieldType.Text)
    private String title;

    /** 文档正文，支持全文检索。 */
    @Field(type = FieldType.Text)
    private String content;

    /** 业务分类，支持精确过滤。 */
    @Field(type = FieldType.Keyword)
    private String category;

    /** 业务标签，支持精确过滤。 */
    @Field(type = FieldType.Keyword)
    private List<String> tags;

    /** 扩展元数据，允许保存业务侧的附加字段。 */
    @Field(type = FieldType.Object)
    private Map<String, Object> metadata;

    /** 创建时间。 */
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant createdAt;

    /** 最近更新时间。 */
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant updatedAt;

    /** Elasticsearch 映射和反序列化使用的无参构造方法。 */
    protected ElasticsearchDocument() {
    }

    /**
     * 创建业务文档。
     *
     * @param id 文档 ID
     * @param title 标题
     * @param content 正文
     * @param category 分类
     * @param tags 标签
     * @param metadata 扩展元数据
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public ElasticsearchDocument(
            String id,
            String title,
            String content,
            String category,
            List<String> tags,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.category = category;
        this.tags = tags;
        this.metadata = metadata;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** @return 文档 ID */
    public String getId() {
        return id;
    }

    /** @return 文档标题 */
    public String getTitle() {
        return title;
    }

    /** @return 文档正文 */
    public String getContent() {
        return content;
    }

    /** @return 文档分类 */
    public String getCategory() {
        return category;
    }

    /** @return 文档标签 */
    public List<String> getTags() {
        return tags;
    }

    /** @return 扩展元数据 */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /** @return 创建时间 */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** @return 最近更新时间 */
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
