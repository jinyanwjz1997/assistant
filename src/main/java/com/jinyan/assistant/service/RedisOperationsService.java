package com.jinyan.assistant.service;

import org.redisson.api.StreamMessageId;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Redis 各数据类型的业务接口，屏蔽底层 Redisson 细节。 */
public interface RedisOperationsService {

    /** 缓存用户画像。
     * @param userId 用户唯一标识
     * @param profile 要缓存的画像内容
     * @param ttl 缓存有效期
     */
    void cacheUserProfile(String userId, String profile, Duration ttl);

    /** 获取缓存的用户画像。
     * @param userId 用户唯一标识
     * @return 缓存内容，未命中时返回 {@code null}
     */
    String getCachedUserProfile(String userId);

    /** 原子增加文章浏览量。
     * @param articleId 文章唯一标识
     * @return 增加后的浏览量
     */
    long incrementArticleViews(String articleId);

    /** 保存用户资料 Hash。
     * @param userId 用户唯一标识
     * @param profile 用户资料字段和值
     */
    void saveUserProfile(String userId, Map<String, String> profile);

    /** 读取用户资料 Hash。
     * @param userId 用户唯一标识
     * @return 用户资料字段和值
     */
    Map<String, String> getUserProfile(String userId);

    /** 添加最近搜索记录。
     * @param userId 用户唯一标识
     * @param keyword 搜索关键词
     * @return 截断后的最近搜索列表
     */
    List<String> addRecentSearch(String userId, String keyword);

    /** 添加关注关系。
     * @param userId 发起关注的用户
     * @param targetUserId 被关注的用户
     * @return 本次是否新增了关注关系
     */
    boolean followUser(String userId, String targetUserId);

    /** 判断关注关系是否存在。
     * @param userId 发起关注的用户
     * @param targetUserId 被关注的用户
     * @return 是否已关注
     */
    boolean isFollowing(String userId, String targetUserId);

    /** 写入排行榜分数。
     * @param board 排行榜名称
     * @param userId 用户唯一标识
     * @param score 排行榜分数
     */
    void recordLeaderboardScore(String board, String userId, double score);

    /** 获取排行榜前 N 名。
     * @param board 排行榜名称
     * @param limit 返回数量
     * @return 按分数从高到低排列的用户标识
     */
    Collection<String> topLeaderboard(String board, int limit);

    /** 标记用户某一天活跃。
     * @param userId 用户唯一标识
     * @param dayOffset 相对起始日的非负偏移量
     * @return 本次是否首次标记该日期
     */
    boolean markDailyActive(String userId, long dayOffset);

    /** 统计用户已标记的活跃天数。
     * @param userId 用户唯一标识
     * @return Bitmap 中已设置的 bit 数量
     */
    long activeDayCount(String userId);

    /** 记录一个访客并估算去重 UV。
     * @param event 活动或页面名称
     * @param visitorId 访客唯一标识
     * @return 本次是否被 HyperLogLog 视为新增样本
     */
    boolean recordUniqueVisitor(String event, String visitorId);

    /** 获取活动的估算 UV。
     * @param event 活动或页面名称
     * @return HyperLogLog 估算的访客数量
     */
    long uniqueVisitorCount(String event);

    /** 注册门店地理位置。
     * @param geoKey 地理集合名称
     * @param storeId 门店唯一标识
     * @param longitude 经度
     * @param latitude 纬度
     * @return Redis 新增或更新的成员数量
     */
    long registerStore(String geoKey, String storeId, double longitude, double latitude);

    /** 查询指定坐标半径内的门店。
     * @param geoKey 地理集合名称
     * @param longitude 查询点经度
     * @param latitude 查询点纬度
     * @param radiusKm 查询半径，单位为公里
     * @return 半径内的门店标识
     */
    List<String> nearbyStores(String geoKey, double longitude, double latitude, double radiusKm);

    /** 发布审计事件到 Stream。
     * @param streamKey Stream 名称
     * @param event 事件字段和值
     * @return Redis 生成的消息 ID
     */
    StreamMessageId publishAuditEvent(String streamKey, Map<String, String> event);

    /** 读取 Stream 中的历史审计事件。
     * @param streamKey Stream 名称
     * @param limit 最大读取数量
     * @return 消息 ID 与消息字段的映射
     */
    Map<StreamMessageId, Map<String, String>> readAuditEvents(String streamKey, int limit);

    /**
     * 在分布式锁保护下执行一段业务逻辑。
     * @param lockName 锁名称
     * @param waitTime 获取锁的最大等待时间
     * @param leaseTime 锁租期，零表示使用 Redisson 看门狗续期
     * @param callback 获取锁后执行的业务回调
     * @param <T> 回调返回值类型
     * @return 回调返回值
     * @throws Exception 回调异常或线程等待锁时被中断
     */
    <T> T executeWithLock(String lockName, Duration waitTime, Duration leaseTime, LockCallback<T> callback)
            throws Exception;

    /** 分布式锁持有期间执行的业务回调。 */
    @FunctionalInterface
    interface LockCallback<T> {

        /** 执行业务逻辑。
         * @return 业务结果
         * @throws Exception 业务执行异常
         */
        T run() throws Exception;
    }
}
