package com.jinyan.assistant.controller;

import com.jinyan.assistant.service.RedisOperationsService;
import org.redisson.api.StreamMessageId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Redis 示例接口，便于本地通过 HTTP 验证各种数据类型。 */
@RestController
@RequestMapping("/api/redis")
public class RedisOperationsController {

    private final RedisOperationsService redis;

    /** 创建 Redis 示例控制器。
     * @param redis Redis 业务服务
     */
    public RedisOperationsController(RedisOperationsService redis) {
        this.redis = redis;
    }

    /** 写入带 TTL 的用户画像缓存。
     * @param userId 用户唯一标识
     * @param profile 画像内容
     * @param ttlSeconds 缓存秒数
     */
    @PutMapping("/profiles/{userId}/cache")
    public void cacheProfile(
            @PathVariable String userId,
            @RequestParam String profile,
            @RequestParam(defaultValue = "300") long ttlSeconds
    ) {
        redis.cacheUserProfile(userId, profile, Duration.ofSeconds(ttlSeconds));
    }

    /** 读取用户画像缓存。
     * @param userId 用户唯一标识
     * @return 缓存内容
     */
    @GetMapping("/profiles/{userId}/cache")
    public String cachedProfile(@PathVariable String userId) {
        return redis.getCachedUserProfile(userId);
    }

    /** 增加文章浏览量并返回最新值。
     * @param articleId 文章唯一标识
     * @return 最新浏览量
     */
    @PostMapping("/articles/{articleId}/views")
    public long incrementArticleViews(@PathVariable String articleId) {
        return redis.incrementArticleViews(articleId);
    }

    /** 保存并返回用户资料 Hash。
     * @param userId 用户唯一标识
     * @param profile 用户资料字段和值
     * @return 保存后的用户资料
     */
    @PutMapping("/profiles/{userId}")
    public Map<String, String> profile(
            @PathVariable String userId,
            @RequestBody Map<String, String> profile
    ) {
        redis.saveUserProfile(userId, profile);
        return redis.getUserProfile(userId);
    }

    /** 添加最近搜索并返回当前列表。
     * @param userId 用户唯一标识
     * @param keyword 搜索关键词
     * @return 最近搜索列表
     */
    @PostMapping("/users/{userId}/searches")
    public List<String> recentSearches(@PathVariable String userId, @RequestParam String keyword) {
        return redis.addRecentSearch(userId, keyword);
    }

    /** 添加用户关注关系。
     * @param userId 发起关注的用户
     * @param targetUserId 被关注的用户
     * @return 本次是否新增关注
     */
    @PostMapping("/users/{userId}/following/{targetUserId}")
    public boolean follow(@PathVariable String userId, @PathVariable String targetUserId) {
        return redis.followUser(userId, targetUserId);
    }

    /** 标记用户活跃日期并返回累计天数。
     * @param userId 用户唯一标识
     * @param dayOffset 相对起始日的天数偏移
     * @return 活跃天数
     */
    @PostMapping("/users/{userId}/active/{dayOffset}")
    public long markActive(@PathVariable String userId, @PathVariable long dayOffset) {
        redis.markDailyActive(userId, dayOffset);
        return redis.activeDayCount(userId);
    }

    /** 记录访客并返回估算 UV。
     * @param event 活动或页面名称
     * @param visitorId 访客唯一标识
     * @return 估算 UV
     */
    @PostMapping("/uv/{event}/{visitorId}")
    public long recordVisitor(@PathVariable String event, @PathVariable String visitorId) {
        redis.recordUniqueVisitor(event, visitorId);
        return redis.uniqueVisitorCount(event);
    }

    /** 注册门店坐标。
     * @param geoKey 地理集合名称
     * @param storeId 门店唯一标识
     * @param longitude 经度
     * @param latitude 纬度
     * @return Redis 新增或更新的成员数量
     */
    @PutMapping("/geo/{geoKey}/{storeId}")
    public long registerStore(
            @PathVariable String geoKey,
            @PathVariable String storeId,
            @RequestParam double longitude,
            @RequestParam double latitude
    ) {
        return redis.registerStore(geoKey, storeId, longitude, latitude);
    }

    /** 查询半径内门店。
     * @param geoKey 地理集合名称
     * @param longitude 查询点经度
     * @param latitude 查询点纬度
     * @param radiusKm 查询半径，单位为公里
     * @return 门店标识列表
     */
    @GetMapping("/geo/{geoKey}/nearby")
    public List<String> nearbyStores(
            @PathVariable String geoKey,
            @RequestParam double longitude,
            @RequestParam double latitude,
            @RequestParam double radiusKm
    ) {
        return redis.nearbyStores(geoKey, longitude, latitude, radiusKm);
    }

    /** 更新排行榜分数并返回 TopN。
     * @param board 排行榜名称
     * @param userId 用户唯一标识
     * @param score 排行榜分数
     * @param limit 返回数量
     * @return 排名靠前的用户标识
     */
    @PutMapping("/leaderboards/{board}/{userId}")
    public Collection<String> score(
            @PathVariable String board,
            @PathVariable String userId,
            @RequestParam double score,
            @RequestParam(defaultValue = "10") int limit
    ) {
        redis.recordLeaderboardScore(board, userId, score);
        return redis.topLeaderboard(board, limit);
    }

    /** 发布一条 Stream 审计事件。
     * @param streamKey Stream 名称
     * @param event 事件字段和值
     * @return Redis 消息 ID
     */
    @PostMapping("/streams/{streamKey}/events")
    public StreamMessageId publishEvent(
            @PathVariable String streamKey,
            @RequestBody Map<String, String> event
    ) {
        return redis.publishAuditEvent(streamKey, event);
    }

    /** 演示在分布式锁保护下执行回调。
     * @param lockName 锁名称
     * @return 锁持有线程信息
     * @throws Exception 获取锁或回调执行异常
     */
    @PostMapping("/locks/{lockName}/demo")
    public String lockDemo(@PathVariable String lockName) throws Exception {
        return redis.executeWithLock(
                lockName,
                Duration.ofSeconds(2),
                Duration.ofSeconds(10),
                () -> "lock acquired by " + Thread.currentThread().getName()
        );
    }
}
