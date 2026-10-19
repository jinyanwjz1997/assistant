# Redis 操作示例

`RedisOperationsService` 使用 Redisson 封装了 Redis 原生数据类型，并为每类数据绑定了一个业务场景：

| 类型 | 方法 | 应用 |
| --- | --- | --- |
| String | `cacheUserProfile` / `incrementArticleViews` | 带 TTL 的用户画像缓存、原子浏览量 |
| Hash | `saveUserProfile` | 按字段更新用户资料 |
| List | `addRecentSearch` | 最近搜索记录，最多保留 20 条 |
| Set | `followUser` | 关注关系去重 |
| Sorted Set | `recordLeaderboardScore` | 积分排行榜和 TopN |
| Bitmap | `markDailyActive` | 用户签到和活跃天数 |
| HyperLogLog | `recordUniqueVisitor` | 低内存估算活动 UV |
| Geo | `registerStore` | 附近门店查询 |
| Stream | `publishAuditEvent` | 可回放的审计事件 |

所有示例 key 都有 `assistant:redis:` 前缀；字符串类对象使用 String codec，便于直接用 `redis-cli` 检查。

## HTTP 快速验证

项目 context-path 是 `/assistant`，需要先启动 Redis（默认 `127.0.0.1:6379`）。

```bash
# String + TTL
curl -X PUT 'http://localhost:8080/assistant/api/redis/profiles/u-1/cache?profile=vip&ttlSeconds=300'
curl 'http://localhost:8080/assistant/api/redis/profiles/u-1/cache'

# List
curl -X POST 'http://localhost:8080/assistant/api/redis/users/u-1/searches?keyword=redis'

# Set
curl -X POST 'http://localhost:8080/assistant/api/redis/users/u-1/following/u-2'

# Sorted Set
curl -X PUT 'http://localhost:8080/assistant/api/redis/leaderboards/monthly/u-1?score=99.5&limit=10'

# Stream
curl -X POST 'http://localhost:8080/assistant/api/redis/streams/audit/events' \
  -H 'Content-Type: application/json' -d '{"action":"login","userId":"u-1"}'

# 分布式锁模板（真实业务请把业务代码放进 executeWithLock 的 callback）
curl -X POST 'http://localhost:8080/assistant/api/redis/locks/order-123/demo'
```

## 分布式锁约定

`executeWithLock` 使用 `tryLock(waitTime, leaseTime)`：等待超时直接失败，固定租期到期自动释放；传入 `Duration.ZERO` 时交给 Redisson 看门狗自动续期。业务异常会原样抛出，`finally` 只在当前线程确实持有锁时解锁，避免误释放其他线程的锁。

RAG 流程已有同样的加解锁实践，详见 `RagSegmentStreamConsumer` 和 `SegmentSummaryServiceImpl`。
