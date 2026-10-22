package com.jinyan.assistant.controller;

import com.jinyan.assistant.entity.ElasticsearchDocumentRequest;
import com.jinyan.assistant.entity.ElasticsearchDocumentSearchRequest;
import com.jinyan.assistant.entity.ElasticsearchDocumentSearchResult;
import com.jinyan.assistant.entity.ElasticsearchDocumentView;
import com.jinyan.assistant.service.ElasticsearchDocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 提供 Elasticsearch 文档索引、查询、更新和删除接口。 */
@RestController
@RequestMapping("/elasticsearch/documents")
public class ElasticsearchDocumentController {

    private final ElasticsearchDocumentService documentService;

    /**
     * 创建 Elasticsearch 文档控制器。
     *
     * @param documentService Elasticsearch 文档服务
     */
    public ElasticsearchDocumentController(ElasticsearchDocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 新建一份 Elasticsearch 文档。
     *
     * @param request 文档内容
     * @return 新建后的文档
     */
    @PostMapping
    public ResponseEntity<ElasticsearchDocumentView> create(
            @RequestBody ElasticsearchDocumentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.save(null, request));
    }

    /**
     * 按 ID 全量更新一份 Elasticsearch 文档。
     *
     * @param id 文档 ID
     * @param request 新的文档内容
     * @return 更新后的文档
     */
    @PutMapping("/{id}")
    public ElasticsearchDocumentView update(
            @PathVariable String id,
            @RequestBody ElasticsearchDocumentRequest request
    ) {
        return documentService.save(id, request);
    }

    /**
     * 按 ID 查询文档。
     *
     * @param id 文档 ID
     * @return 文档内容
     */
    @GetMapping("/{id}")
    public ElasticsearchDocumentView get(@PathVariable String id) {
        return documentService.get(id);
    }

    /**
     * 按关键词、分类和标签分页查询文档。
     *
     * @param request 组合查询条件
     * @return 分页查询结果
     */
    @PostMapping("/_search")
    public ElasticsearchDocumentSearchResult search(
            @RequestBody(required = false) ElasticsearchDocumentSearchRequest request
    ) {
        return documentService.search(request);
    }

    /**
     * 删除一份文档。
     *
     * @param id 文档 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        documentService.delete(id);
        return Map.of("id", id, "deleted", true);
    }

    /**
     * 手动创建业务索引并写入映射，适合部署或发布阶段调用一次。
     *
     * @return 索引初始化结果
     */
    @PostMapping("/_index")
    public Map<String, Object> ensureIndex() {
        return Map.of("created", documentService.ensureIndex());
    }

    /**
     * 统计业务索引中的文档数量。
     *
     * @return 文档数量
     */
    @GetMapping("/_count")
    public Map<String, Object> count() {
        return Map.of("count", documentService.count());
    }
}
