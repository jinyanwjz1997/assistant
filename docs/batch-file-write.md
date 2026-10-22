# 文件数据多线程批量写入

接口将输入数据按固定大小切分，使用专用有界线程池并行写入
`jinyan.tb_file_info`。每个批次使用独立的 `REQUIRES_NEW` 事务：批次内任意数据失败时，
该批全部回滚；其他已经提交的批次不受影响。

## 请求

```http
POST /assistant/files/batch
Content-Type: application/json
```

```json
{
  "files": [
    {
      "fileName": "document-1.txt",
      "fileContext": "第一份文件正文"
    },
    {
      "fileName": "document-2.txt",
      "fileContext": "第二份文件正文"
    }
  ]
}
```

全部成功返回 `200 OK`；存在失败批次时返回 `207 Multi-Status`。响应中的 `items` 按请求
顺序排列，失败记录的 `fileId` 为空，并携带 `DATABASE_BATCH_WRITE_FAILED` 错误码。

## 配置

```yaml
assistant:
  database:
    batch-write:
      parallelism: 4
      batch-size: 100
      queue-capacity: 100
      max-records: 10000
```

- `parallelism` 必须结合 Hikari 最大连接数设置，默认 4，为普通查询和 RAG 后台任务预留连接。
- `batch-size` 同时作为事务大小；值越大吞吐可能越高，但失败回滚范围和事务占用时间也越大。
- `queue-capacity` 达到上限后由 HTTP 请求线程执行任务，通过自然降速形成背压。
- `max-records` 防止单个请求长期占用线程池和数据库连接。
- PostgreSQL JDBC URL 应保留 `reWriteBatchedInserts=true`，让驱动合并 Hibernate 批量 INSERT。

该接口只负责写入文件原文，不会触发 RAG 切片、摘要或向量化。如需进入知识库，应继续使用
`/embedding/uploadDocument` 或后续增加专门的“批量入库并向量化”任务接口。
