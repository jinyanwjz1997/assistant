package com.jinyan.assistant.service;

import com.jinyan.assistant.entity.ElasticsearchDocumentRequest;
import com.jinyan.assistant.entity.ElasticsearchDocumentSearchRequest;
import com.jinyan.assistant.entity.ElasticsearchDocumentSearchResult;
import com.jinyan.assistant.entity.ElasticsearchDocumentView;

/** 提供 Elasticsearch 业务文档的索引、查询和删除能力。 */
public interface ElasticsearchDocumentService {

    /**
     * 创建或更新一份 Elasticsearch 文档。
     *
     * @param id 文档 ID；为空时由服务生成 UUID
     * @param request 文档写入请求
     * @return 保存后的文档
     */
    ElasticsearchDocumentView save(String id, ElasticsearchDocumentRequest request);

    /**
     * 根据 ID 查询一份文档。
     *
     * @param id 文档 ID
     * @return 文档内容
     */
    ElasticsearchDocumentView get(String id);

    /**
     * 根据关键词、分类和标签进行分页查询。
     *
     * @param request 查询条件
     * @return 分页查询结果
     */
    ElasticsearchDocumentSearchResult search(ElasticsearchDocumentSearchRequest request);

    /**
     * 删除一份 Elasticsearch 文档。
     *
     * @param id 文档 ID
     */
    void delete(String id);

    /**
     * 创建业务索引并写入实体映射。
     *
     * @return 本次调用创建索引时返回 {@code true}，索引已存在时返回 {@code false}
     */
    boolean ensureIndex();

    /**
     * 统计业务索引中的文档数量。
     *
     * @return 文档数量
     */
    long count();
}
