package com.jinyan.assistant.service.impl;

import com.jinyan.assistant.config.ElasticsearchProperties;
import com.jinyan.assistant.entity.ElasticsearchDocument;
import com.jinyan.assistant.entity.ElasticsearchDocumentHit;
import com.jinyan.assistant.entity.ElasticsearchDocumentRequest;
import com.jinyan.assistant.entity.ElasticsearchDocumentSearchRequest;
import com.jinyan.assistant.entity.ElasticsearchDocumentSearchResult;
import com.jinyan.assistant.entity.ElasticsearchDocumentView;
import com.jinyan.assistant.exception.ApiException;
import com.jinyan.assistant.service.ElasticsearchDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 使用 Spring Data Elasticsearch 官方客户端操作业务文档。 */
@Service
@Slf4j
public class ElasticsearchDocumentServiceImpl implements ElasticsearchDocumentService {

    private static final int MAX_TITLE_LENGTH = 256;

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchProperties properties;

    /**
     * 创建 Elasticsearch 文档服务。
     *
     * @param elasticsearchOperations Spring Data Elasticsearch 操作模板
     * @param properties Elasticsearch 业务索引配置
     */
    public ElasticsearchDocumentServiceImpl(
            ElasticsearchOperations elasticsearchOperations,
            ElasticsearchProperties properties
    ) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.properties = properties;
        this.properties.validate();
    }

    /**
     * 创建或更新一份文档。更新时保留原创建时间，只刷新更新时间。
     *
     * @param id 文档 ID；为空时自动生成 UUID
     * @param request 文档写入请求
     * @return 保存后的文档
     */
    @Override
    public ElasticsearchDocumentView save(String id, ElasticsearchDocumentRequest request) {
        validateRequest(request);
        String documentId = StringUtils.hasText(id) ? id.trim() : UUID.randomUUID().toString();
        Instant now = Instant.now();
        ElasticsearchDocument existing = null;
        if (StringUtils.hasText(id)) {
            try {
                existing = findDocument(documentId);
            } catch (RuntimeException exception) {
                throw unavailable(exception);
            }
        }
        Instant createdAt = existing == null || existing.getCreatedAt() == null
                ? now
                : existing.getCreatedAt();

        ElasticsearchDocument document = new ElasticsearchDocument(
                documentId,
                request.title().trim(),
                request.content(),
                normalizeOptional(request.category()),
                normalizeTags(request.tags()),
                copyMetadata(request.metadata()),
                createdAt,
                now
        );

        try {
            ensureIndex();
            ElasticsearchDocument saved = elasticsearchOperations.save(document, indexCoordinates());
            return ElasticsearchDocumentView.from(saved);
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    /**
     * 查询文档，不存在时返回统一的 404 业务异常。
     *
     * @param id 文档 ID
     * @return 文档内容
     */
    @Override
    public ElasticsearchDocumentView get(String id) {
        String documentId = requireId(id);
        try {
            ElasticsearchDocument document = findDocument(documentId);
            if (document == null) {
                throw new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ELASTICSEARCH_DOCUMENT_NOT_FOUND",
                        "Elasticsearch 文档不存在: " + documentId
                );
            }
            return ElasticsearchDocumentView.from(document);
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    /**
     * 根据关键词、分类和标签执行组合查询。
     *
     * @param request 查询条件，为空时按默认分页查询全部文档
     * @return 分页查询结果
     */
    @Override
    public ElasticsearchDocumentSearchResult search(ElasticsearchDocumentSearchRequest request) {
        ElasticsearchDocumentSearchRequest actualRequest = request == null
                ? new ElasticsearchDocumentSearchRequest(null, null, null, null, null)
                : request;
        int page = actualRequest.resolvedPage();
        int size = actualRequest.resolvedSize();

        try {
            Criteria criteria = buildCriteria(actualRequest);
            Query query = new CriteriaQuery(criteria)
                    .setPageable(PageRequest.of(page, size));
            SearchHits<ElasticsearchDocument> hits = elasticsearchOperations.search(
                    query,
                    ElasticsearchDocument.class,
                    indexCoordinates()
            );
            List<ElasticsearchDocumentHit> items = hits.getSearchHits().stream()
                    .map(this::toHit)
                    .toList();
            return new ElasticsearchDocumentSearchResult(
                    hits.getTotalHits(),
                    page,
                    size,
                    items
            );
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    /**
     * 删除一份文档。删除前检查文档是否存在，避免把成功响应误解为实际删除。
     *
     * @param id 文档 ID
     */
    @Override
    public void delete(String id) {
        String documentId = requireId(id);
        try {
            if (findDocument(documentId) == null) {
                throw new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ELASTICSEARCH_DOCUMENT_NOT_FOUND",
                        "Elasticsearch 文档不存在: " + documentId
                );
            }
            elasticsearchOperations.delete(documentId, indexCoordinates());
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    /**
     * 确保索引存在，并根据实体上的 {@code @Field} 注解写入映射。
     *
     * @return 本次调用创建索引时返回 {@code true}
     */
    @Override
    public boolean ensureIndex() {
        try {
            IndexOperations indexOperations = elasticsearchOperations.indexOps(indexCoordinates());
            if (indexOperations.exists()) {
                return false;
            }
            boolean created = indexOperations.create();
            if (created) {
                Document mapping = indexOperations.createMapping(ElasticsearchDocument.class);
                indexOperations.putMapping(mapping);
            }
            return created;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    /**
     * 统计索引中的文档数量。
     *
     * @return 文档数量
     */
    @Override
    public long count() {
        try {
            return elasticsearchOperations.count(
                    Query.findAll(),
                    ElasticsearchDocument.class,
                    indexCoordinates()
            );
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    /**
     * 构造组合条件。关键词使用标题和正文的 OR，分类和标签与关键词使用 AND。
     *
     * @param request 查询请求
     * @return Criteria 查询条件
     */
    private Criteria buildCriteria(ElasticsearchDocumentSearchRequest request) {
        Criteria criteria = null;
        if (StringUtils.hasText(request.keyword())) {
            Criteria keywordCriteria = new Criteria("title")
                    .matches(request.keyword().trim())
                    .or(new Criteria("content").matches(request.keyword().trim()));
            criteria = keywordCriteria;
        }
        if (StringUtils.hasText(request.category())) {
            criteria = and(criteria, new Criteria("category").is(request.category().trim()));
        }
        if (request.tags() != null && !request.tags().isEmpty()) {
            List<String> tags = request.tags().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
            if (!tags.isEmpty()) {
                criteria = and(criteria, new Criteria("tags").in(tags));
            }
        }
        return criteria == null ? new Criteria() : criteria;
    }

    /**
     * 将一个条件追加到现有条件的 AND 分支。
     *
     * @param current 当前条件
     * @param next 待追加条件
     * @return 合并后的条件
     */
    private Criteria and(Criteria current, Criteria next) {
        return current == null ? next : current.and(next);
    }

    /**
     * 查找底层文档。
     *
     * @param id 文档 ID
     * @return 文档，不存在时返回 {@code null}
     */
    private ElasticsearchDocument findDocument(String id) {
        return elasticsearchOperations.get(id, ElasticsearchDocument.class, indexCoordinates());
    }

    /**
     * 将 Spring Data 搜索命中转换为接口结果。
     *
     * @param hit Spring Data 搜索命中
     * @return 带得分的接口结果
     */
    private ElasticsearchDocumentHit toHit(SearchHit<ElasticsearchDocument> hit) {
        return new ElasticsearchDocumentHit(
                hit.getScore(),
                ElasticsearchDocumentView.from(hit.getContent())
        );
    }

    /**
     * 校验写入参数。
     *
     * @param request 写入请求
     * @throws IllegalArgumentException 当请求不合法时抛出
     */
    private void validateRequest(ElasticsearchDocumentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (!StringUtils.hasText(request.title())) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (request.title().trim().length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("title must not exceed 256 characters");
        }
        if (!StringUtils.hasText(request.content())) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }

    /**
     * 校验文档 ID。
     *
     * @param id 原始 ID
     * @return 去除首尾空格后的 ID
     */
    private String requireId(String id) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("id must not be blank");
        }
        return id.trim();
    }

    /**
     * 标准化可选字符串。
     *
     * @param value 原始字符串
     * @return 空白字符串转换为 {@code null} 的结果
     */
    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 标准化标签并返回不可变列表。
     *
     * @param tags 原始标签
     * @return 标准化标签
     */
    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    /**
     * 复制元数据，避免调用方在保存后修改对象影响当前业务对象。
     *
     * @param metadata 原始元数据
     * @return 可变副本或空映射
     */
    private Map<String, Object> copyMetadata(Map<String, Object> metadata) {
        return metadata == null
                ? Collections.emptyMap()
                : new LinkedHashMap<>(metadata);
    }

    /**
     * 获取当前业务索引坐标。
     *
     * @return 索引坐标
     */
    private IndexCoordinates indexCoordinates() {
        return IndexCoordinates.of(properties.getIndexName());
    }

    /**
     * 将底层 Elasticsearch 异常转换为统一服务不可用异常。
     *
     * @param exception 底层异常
     * @return 统一业务异常
     */
    private ApiException unavailable(RuntimeException exception) {
        log.error("Elasticsearch 操作失败, index:{}", properties.getIndexName(), exception);
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ELASTICSEARCH_UNAVAILABLE",
                "Elasticsearch 服务暂不可用，请稍后重试"
        );
    }
}
