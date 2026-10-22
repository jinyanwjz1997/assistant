# Elasticsearch 文档接口

项目使用 Spring Data Elasticsearch 调用 Elasticsearch，业务访问集中在
`ElasticsearchDocumentService`，控制器不直接操作 Elasticsearch 客户端。

## 连接配置

连接信息通过环境变量注入：

```bash
export ELASTICSEARCH_URIS=http://127.0.0.1:9200
export ELASTICSEARCH_USERNAME=elastic
export ELASTICSEARCH_PASSWORD='change-me'
export ELASTICSEARCH_INDEX_NAME=assistant_documents
```

应用上下文路径是 `/assistant`，因此完整接口地址以
`http://127.0.0.1:8080/assistant` 开头。

## 索引初始化

部署后调用一次：

```bash
curl -X POST http://127.0.0.1:8080/assistant/elasticsearch/documents/_index
```

服务会根据 `ElasticsearchDocument` 的 `@Field` 注解创建字段映射。写入接口在索引不存在时也会自动初始化。

## 新建文档

```bash
curl -X POST http://127.0.0.1:8080/assistant/elasticsearch/documents \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "退款规则",
    "content": "普通商品支持在收货后七天内申请退款。",
    "category": "after-sale",
    "tags": ["退款", "售后"],
    "metadata": {
      "source": "manual",
      "version": 1
    }
  }'
```

## 查询文档

```bash
curl -X POST http://127.0.0.1:8080/assistant/elasticsearch/documents/_search \
  -H 'Content-Type: application/json' \
  -d '{
    "keyword": "退款",
    "category": "after-sale",
    "tags": ["售后"],
    "page": 0,
    "size": 20
  }'
```

关键词会在 `title` 和 `content` 中进行全文匹配；分类和标签属于精确过滤条件，多个条件之间使用 AND。

## 查询、更新和删除

```bash
curl http://127.0.0.1:8080/assistant/elasticsearch/documents/{id}

curl -X PUT http://127.0.0.1:8080/assistant/elasticsearch/documents/{id} \
  -H 'Content-Type: application/json' \
  -d '{"title":"新标题","content":"更新后的正文"}'

curl -X DELETE http://127.0.0.1:8080/assistant/elasticsearch/documents/{id}
```

更新接口是全量更新：未传入的可选字段会被保存为空值或空集合，不会自动合并旧字段；如果 ID 尚不存在，PUT 会按指定 ID 新建文档。

统计索引中的文档数量：

```bash
curl http://127.0.0.1:8080/assistant/elasticsearch/documents/_count
```
