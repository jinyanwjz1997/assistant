package com.jinyan.assistant.service.impl; // 声明 Redis 服务实现所在的包。

import com.jinyan.assistant.service.RedisOperationsService; // 引入 Redis 业务接口。
import org.redisson.api.GeoUnit; // 引入地理距离单位。
import org.redisson.api.RAtomicLong; // 引入 Redis 原子长整型。
import org.redisson.api.RBitSet; // 引入 Redis Bitmap 操作对象。
import org.redisson.api.RBucket; // 引入 Redis String 操作对象。
import org.redisson.api.RGeo; // 引入 Redis Geo 操作对象。
import org.redisson.api.RHyperLogLog; // 引入 Redis HyperLogLog 操作对象。
import org.redisson.api.RList; // 引入 Redis List 操作对象。
import org.redisson.api.RLock; // 引入 Redisson 分布式锁。
import org.redisson.api.RMap; // 引入 Redis Hash 操作对象。
import org.redisson.api.RScoredSortedSet; // 引入 Redis Sorted Set 操作对象。
import org.redisson.api.RSet; // 引入 Redis Set 操作对象。
import org.redisson.api.RStream; // 引入 Redis Stream 操作对象。
import org.redisson.api.RedissonClient; // 引入 Redisson 客户端。
import org.redisson.api.StreamMessageId; // 引入 Stream 消息 ID。
import org.redisson.api.stream.StreamAddArgs; // 引入 Stream 消息写入参数。
import org.redisson.client.codec.StringCodec; // 引入字符串编解码器，方便 redis-cli 查看数据。
import org.springframework.stereotype.Service; // 引入 Spring Service 注解。

import java.time.Duration; // 引入时间长度类型。
import java.util.Collection; // 引入通用集合类型。
import java.util.LinkedHashMap; // 引入保持迭代顺序的 Map 实现。
import java.util.List; // 引入列表类型。
import java.util.Map; // 引入键值映射类型。
import java.util.Objects; // 引入空值校验工具。
import java.util.concurrent.TimeUnit; // 引入时间单位枚举。

/** Redis 数据类型的 Redisson 实现，负责 key 规范化和参数校验。 */
@Service // 将实现类注册为 Spring Bean。
public class RedisOperationsServiceImpl implements RedisOperationsService { // 实现 Redis 业务接口。

    private static final String KEY_PREFIX = "assistant:redis:"; // 定义统一 key 前缀，隔离项目数据。
    private static final int MAX_RECENT_SEARCHES = 20; // 定义每个用户最多保留的搜索记录数量。

    private final RedissonClient redissonClient; // 保存线程安全的 Redisson 客户端依赖。

    /**
     * 创建 Redis 操作实现。
     *
     * <p>Redis 数据类型：不直接对应数据类型，负责注入所有 Redis 操作共用的 Redisson 客户端。
     * 这样设计是为了复用线程安全的客户端连接，并通过构造器注入保证依赖明确且便于测试。
     *
     * @param redissonClient Redisson 客户端
     */
    public RedisOperationsServiceImpl(RedissonClient redissonClient) { // 通过构造器注入客户端。
        this.redissonClient = redissonClient; // 保存客户端供各业务方法复用。
    } // 结束构造器。

    /**
     * 将用户画像写入带过期时间的 String。
     *
     * <p>Redis 数据类型：String。用户画像作为一个整体读写，不需要按字段修改，因此 String 最简单。
     * 设置 TTL 可以让过期缓存自动删除，避免旧数据长期占用内存，并允许缓存未命中时从数据源重新加载。
     *
     * @param userId 用户唯一标识
     * @param profile 要缓存的画像内容
     * @param ttl 缓存有效期
     */
    @Override // 实现接口定义的用户画像缓存方法。
    public void cacheUserProfile(String userId, String profile, Duration ttl) { // 接收用户、画像和有效期。
        requirePositive(ttl, "ttl"); // 校验缓存有效期必须大于零。
        bucket("profile:" + requireText(userId, "userId")) // 获取当前用户的 String Bucket。
                .set(profile, ttl.toMillis(), TimeUnit.MILLISECONDS); // 写入画像并设置毫秒级 TTL。
    } // 结束用户画像缓存方法。

    /**
     * 获取缓存的用户画像。
     *
     * <p>Redis 数据类型：String。通过与写入方法相同的 key 直接读取完整画像，单次 GET 的复杂度低；
     * 缓存不存在或已经过期时返回 {@code null}，由上层业务决定是否回源。
     *
     * @param userId 用户唯一标识
     * @return 缓存内容，未命中时返回 {@code null}
     */
    @Override // 实现接口定义的用户画像读取方法。
    public String getCachedUserProfile(String userId) { // 接收要查询的用户标识。
        return bucket("profile:" + requireText(userId, "userId")).get(); // 返回画像缓存或空值。
    } // 结束用户画像读取方法。

    /**
     * 原子增加文章浏览量。
     *
     * <p>Redis 数据类型：String 数值，对应 Redisson 的 {@link RAtomicLong}。
     * 使用 Redis 原子自增可以避免多个应用实例并发执行“读取、加一、写回”造成浏览量丢失。
     *
     * @param articleId 文章唯一标识
     * @return 增加后的浏览量
     */
    @Override // 实现接口定义的浏览量自增方法。
    public long incrementArticleViews(String articleId) { // 接收文章标识。
        RAtomicLong views = redissonClient.getAtomicLong( // 获取文章浏览量原子计数器。
                KEY_PREFIX + "article:views:" + requireText(articleId, "articleId")); // 构造并校验计数器 key。
        return views.incrementAndGet(); // 原子加一并返回最新浏览量。
    } // 结束浏览量自增方法。

    /**
     * 使用 Hash 保存用户资料字段。
     *
     * <p>Redis 数据类型：Hash。姓名、邮箱等资料天然由多个字段组成，Hash 可以单独修改某个字段，
     * 无需反序列化和覆盖整份对象，也便于使用 redis-cli 查看指定字段。
     *
     * @param userId 用户唯一标识
     * @param profile 用户资料字段和值
     */
    @Override // 实现接口定义的用户资料保存方法。
    public void saveUserProfile(String userId, Map<String, String> profile) { // 接收用户标识和资料字段。
        Objects.requireNonNull(profile, "profile"); // 拒绝空的资料 Map 引用。
        userProfile(userId).putAll(profile); // 批量写入用户资料 Hash。
    } // 结束用户资料保存方法。

    /**
     * 读取用户资料 Hash。
     *
     * <p>Redis 数据类型：Hash。一次读取全部字段，适合详情页等需要完整用户资料的场景；
     * 返回普通 {@link LinkedHashMap}，避免业务层持有能够继续远程操作 Redis 的对象。
     *
     * @param userId 用户唯一标识
     * @return 用户资料字段和值
     */
    @Override // 实现接口定义的用户资料读取方法。
    public Map<String, String> getUserProfile(String userId) { // 接收用户标识。
        return new LinkedHashMap<>(userProfile(userId).readAllMap()); // 返回与 Redis 客户端解耦的资料副本。
    } // 结束用户资料读取方法。

    /**
     * 添加最近搜索记录，并限制列表最大长度。
     *
     * <p>Redis 数据类型：List。搜索记录具有明确的先后顺序，List 能按写入顺序保存重复关键词；
     * 写入后裁剪到固定长度，是为了防止历史记录无限增长并控制 Redis 内存占用。
     *
     * @param userId 用户唯一标识
     * @param keyword 搜索关键词
     * @return 截断后的最近搜索列表
     */
    @Override // 实现接口定义的最近搜索方法。
    public List<String> addRecentSearch(String userId, String keyword) { // 接收用户标识和搜索词。
        RList<String> searches = list("searches:" + requireText(userId, "userId")); // 获取用户搜索 List。
        searches.add(requireText(keyword, "keyword")); // 将非空搜索词追加到列表尾部。
        if (searches.size() > MAX_RECENT_SEARCHES) { // 判断列表是否超过保留上限。
            searches.trim(0, MAX_RECENT_SEARCHES - 1); // 删除上限之外的搜索记录。
        } // 结束长度限制分支。
        return searches.readAll(); // 返回截断后的全部搜索记录。
    } // 结束最近搜索方法。

    /**
     * 添加用户关注关系。
     *
     * <p>Redis 数据类型：Set。一个用户不能重复关注同一个目标，Set 的成员唯一性可以天然去重；
     * {@code add} 的返回值还能直接说明本次操作是新增关注还是重复请求。
     *
     * @param userId 发起关注的用户
     * @param targetUserId 被关注的用户
     * @return 本次是否新增了关注关系
     */
    @Override // 实现接口定义的关注方法。
    public boolean followUser(String userId, String targetUserId) { // 接收关注双方的用户标识。
        return set("following:" + requireText(userId, "userId")) // 获取当前用户的关注 Set。
                .add(requireText(targetUserId, "targetUserId")); // 添加目标用户并返回是否为新增成员。
    } // 结束关注方法。

    /**
     * 判断用户是否关注目标用户。
     *
     * <p>Redis 数据类型：Set。关注关系只需要判断成员是否存在，不关心排列顺序，
     * 使用 Set 的成员查询可以直接完成判断，且无需把全部关注列表加载到应用内存。
     *
     * @param userId 发起关注的用户
     * @param targetUserId 被关注的用户
     * @return 是否已关注
     */
    @Override // 实现接口定义的关注判断方法。
    public boolean isFollowing(String userId, String targetUserId) { // 接收关注双方的用户标识。
        return set("following:" + requireText(userId, "userId")) // 获取当前用户的关注 Set。
                .contains(requireText(targetUserId, "targetUserId")); // 判断目标用户是否为 Set 成员。
    } // 结束关注判断方法。

    /**
     * 写入排行榜成员分数。
     *
     * <p>Redis 数据类型：Sorted Set。每个用户是唯一成员，排行榜分数作为 score 自动参与排序；
     * 对同一用户再次写入会更新分数，因此既能防止重复成员，也不需要应用手工维护排序结果。
     *
     * @param board 排行榜名称
     * @param userId 用户唯一标识
     * @param score 排行榜分数
     */
    @Override // 实现接口定义的排行榜写入方法。
    public void recordLeaderboardScore(String board, String userId, double score) { // 接收榜单、用户和分数。
        leaderboard(board).add(score, requireText(userId, "userId")); // 新增或更新用户分数。
    } // 结束排行榜写入方法。

    /**
     * 获取排行榜前 N 名。
     *
     * <p>Redis 数据类型：Sorted Set。排行榜需要按分数由高到低读取，因此使用倒序区间查询；
     * Redis 只返回指定范围，避免应用读取全部用户后再排序和截断。
     *
     * @param board 排行榜名称
     * @param limit 返回数量
     * @return 按分数从高到低排列的用户标识
     */
    @Override // 实现接口定义的排行榜查询方法。
    public Collection<String> topLeaderboard(String board, int limit) { // 接收榜单名称和返回数量。
        if (limit < 1) { // 校验返回数量是否为正数。
            throw new IllegalArgumentException("limit must be positive"); // 拒绝无效的返回数量。
        } // 结束返回数量校验分支。
        return leaderboard(board).valueRangeReversed(0, limit - 1); // 按分数倒序返回前 N 名。
    } // 结束排行榜查询方法。

    /**
     * 使用 Bitmap 标记用户某一天活跃。
     *
     * <p>Redis 数据类型：Bitmap。日期偏移作为 bit 下标，一天只占一个 bit，适合保存大量布尔状态；
     * 与每天保存一个 String 或 Set 成员相比，可以显著降低连续签到、活跃记录的内存成本。
     *
     * @param userId 用户唯一标识
     * @param dayOffset 相对起始日的非负偏移量
     * @return 该 bit 设置前的旧值
     */
    @Override // 实现接口定义的活跃标记方法。
    public boolean markDailyActive(String userId, long dayOffset) { // 接收用户标识和日期偏移。
        if (dayOffset < 0) { // 校验日期偏移不能为负数。
            throw new IllegalArgumentException("dayOffset must not be negative"); // 拒绝无效日期偏移。
        } // 结束日期偏移校验分支。
        return bitmap("active:" + requireText(userId, "userId")).set(dayOffset); // 设置对应 bit 并返回旧值。
    } // 结束活跃标记方法。

    /**
     * 统计用户 Bitmap 中的活跃天数。
     *
     * <p>Redis 数据类型：Bitmap。通过位计数直接统计值为 1 的日期数量，
     * 不需要逐日读取数据或在应用内遍历，适合快速计算累计签到和活跃天数。
     *
     * @param userId 用户唯一标识
     * @return 已设置的 bit 数量
     */
    @Override // 实现接口定义的活跃天数统计方法。
    public long activeDayCount(String userId) { // 接收用户标识。
        return bitmap("active:" + requireText(userId, "userId")).cardinality(); // 统计值为 true 的 bit 数量。
    } // 结束活跃天数统计方法。

    /**
     * 将访客加入活动的 HyperLogLog。
     *
     * <p>Redis 数据类型：HyperLogLog。UV 只关心不同访客的近似数量，不需要保存和返回全部访客；
     * HyperLogLog 使用固定的小内存进行基数估算，代价是结果存在可接受的统计误差。
     *
     * @param event 活动或页面名称
     * @param visitorId 访客唯一标识
     * @return HyperLogLog 内容是否发生变化
     */
    @Override // 实现接口定义的 UV 记录方法。
    public boolean recordUniqueVisitor(String event, String visitorId) { // 接收活动名称和访客标识。
        return visitors(event).add(requireText(visitorId, "visitorId")); // 添加访客样本并返回结构是否变化。
    } // 结束 UV 记录方法。

    /**
     * 获取活动的估算 UV。
     *
     * <p>Redis 数据类型：HyperLogLog。直接读取基数估算结果，避免将大量访客 ID 拉取到应用去重；
     * 适用于访问量报表等允许少量误差、但更重视内存成本和查询速度的场景。
     *
     * @param event 活动或页面名称
     * @return HyperLogLog 估算的访客数量
     */
    @Override // 实现接口定义的 UV 查询方法。
    public long uniqueVisitorCount(String event) { // 接收活动或页面名称。
        return visitors(event).count(); // 返回 HyperLogLog 的基数估算值。
    } // 结束 UV 查询方法。

    /**
     * 将门店坐标写入 Geo 集合。
     *
     * <p>Redis 数据类型：Geo。Redis 会把经纬度编码并存入底层 Sorted Set，支持空间距离运算；
     * 使用 Geo 可以避免应用自行实现坐标索引和球面距离计算。
     *
     * @param geoKey 地理集合名称
     * @param storeId 门店唯一标识
     * @param longitude 经度
     * @param latitude 纬度
     * @return Redis 新增或更新的成员数量
     */
    @Override // 实现接口定义的门店坐标注册方法。
    public long registerStore(String geoKey, String storeId, double longitude, double latitude) { // 接收集合、门店和坐标。
        return geo(geoKey).add(longitude, latitude, requireText(storeId, "storeId")); // 写入坐标并返回影响数量。
    } // 结束门店坐标注册方法。

    /**
     * 查询指定坐标半径内的门店。
     *
     * <p>Redis 数据类型：Geo。通过经纬度和公里半径执行空间范围查询，
     * 让 Redis 在服务端筛选附近门店，避免把所有门店坐标读入应用后逐一计算距离。
     *
     * @param geoKey 地理集合名称
     * @param longitude 查询点经度
     * @param latitude 查询点纬度
     * @param radiusKm 查询半径，单位为公里
     * @return 半径内的门店标识
     */
    @Override // 实现接口定义的附近门店查询方法。
    public List<String> nearbyStores(String geoKey, double longitude, double latitude, double radiusKm) { // 接收集合和查询范围。
        if (radiusKm <= 0) { // 校验查询半径是否为正数。
            throw new IllegalArgumentException("radiusKm must be positive"); // 拒绝无效查询半径。
        } // 结束查询半径校验分支。
        return geo(geoKey).radius(longitude, latitude, radiusKm, GeoUnit.KILOMETERS); // 按公里查询范围内门店。
    } // 结束附近门店查询方法。

    /**
     * 发布一条审计事件到 Stream。
     *
     * <p>Redis 数据类型：Stream。审计事件需要保持时间顺序、生成唯一消息 ID，并支持后续消费和回放；
     * 使用字段 Map 写入消息，能够保留结构化事件内容，也便于扩展消费组实现可靠异步处理。
     *
     * @param streamKey Stream 名称
     * @param event 事件字段和值
     * @return Redis 生成的消息 ID
     */
    @Override // 实现接口定义的审计事件发布方法。
    public StreamMessageId publishAuditEvent(String streamKey, Map<String, String> event) { // 接收 Stream 名称和事件内容。
        Objects.requireNonNull(event, "event"); // 拒绝空的事件 Map 引用。
        if (event.isEmpty()) { // 校验事件至少包含一个字段。
            throw new IllegalArgumentException("event must not be empty"); // 拒绝没有字段的事件。
        } // 结束事件内容校验分支。
        return stream(streamKey).add(StreamAddArgs.entries(event)); // 追加事件并返回消息 ID。
    } // 结束审计事件发布方法。

    /**
     * 读取 Stream 中的历史审计事件。
     *
     * <p>Redis 数据类型：Stream。按消息 ID 范围读取可以保留事件顺序并限制返回数量；
     * 这种方式适合审计查询和故障回放，不会像 Pub/Sub 一样因消费者离线而丢失历史消息。
     *
     * @param streamKey Stream 名称
     * @param limit 最大读取数量
     * @return 消息 ID 与消息字段的映射
     */
    @Override // 实现接口定义的审计事件读取方法。
    public Map<StreamMessageId, Map<String, String>> readAuditEvents(String streamKey, int limit) { // 接收 Stream 名称和数量。
        if (limit < 1) { // 校验读取数量是否为正数。
            throw new IllegalArgumentException("limit must be positive"); // 拒绝无效读取数量。
        } // 结束读取数量校验分支。
        return stream(streamKey).range(limit, StreamMessageId.MIN, StreamMessageId.MAX); // 从最早消息开始读取指定数量。
    } // 结束审计事件读取方法。

    /**
     * 在分布式锁保护下执行一段业务逻辑。
     *
     * <p>Redis 能力：基于 Redis key 和 Lua 脚本实现的 Redisson 分布式锁，不属于五种基础数据类型。
     * 使用它是为了让多个线程、进程或应用实例互斥执行同一业务；等待时间避免请求无限阻塞，
     * 租期或看门狗避免持锁实例宕机后形成永久死锁，持有者校验则防止误释放其他线程的锁。
     *
     * @param lockName 锁名称
     * @param waitTime 获取锁的最大等待时间
     * @param leaseTime 锁租期，零表示使用 Redisson 看门狗续期
     * @param callback 获取锁后执行的业务回调
     * @param <T> 回调返回值类型
     * @return 回调返回值
     * @throws Exception 回调异常或线程等待锁时被中断
     */
    @Override // 实现接口定义的分布式锁执行模板。
    public <T> T executeWithLock( // 声明支持任意返回值类型的锁模板。
            String lockName, // 接收用于构造 Redis key 的锁名称。
            Duration waitTime, // 接收获取锁的最大等待时间。
            Duration leaseTime, // 接收锁的租期或看门狗标记。
            LockCallback<T> callback // 接收拿到锁后执行的业务回调。
    ) throws Exception { // 允许向调用方传播中断和业务异常。
        requirePositive(waitTime, "waitTime"); // 校验等待时间必须大于零。
        Objects.requireNonNull(leaseTime, "leaseTime"); // 校验锁租期不能为空。
        if (leaseTime.isNegative()) { // 判断锁租期是否小于零。
            throw new IllegalArgumentException("leaseTime must not be negative"); // 拒绝负数租期。
        } // 结束锁租期校验分支。
        Objects.requireNonNull(callback, "callback"); // 校验业务回调不能为空。

        RLock lock = redissonClient.getLock(KEY_PREFIX + "lock:" + requireText(lockName, "lockName")); // 获取命名分布式锁。
        boolean locked = false; // 记录当前线程是否成功获得锁。
        try { // 进入获取锁和执行回调的保护区。
            locked = leaseTime.isZero() // 根据租期是否为零选择看门狗或固定租期模式。
                    ? lock.tryLock(waitTime.toMillis(), TimeUnit.MILLISECONDS) // 使用看门狗自动续期模式尝试加锁。
                    : lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS); // 使用固定租期模式尝试加锁。
            if (!locked) { // 判断等待时间内是否未获得锁。
                throw new IllegalStateException("Could not acquire Redis lock: " + lockName); // 向调用方报告加锁失败。
            } // 结束加锁结果判断分支。
            return callback.run(); // 在锁保护下执行回调并返回业务结果。
        } catch (InterruptedException exception) { // 捕获等待锁期间的线程中断。
            Thread.currentThread().interrupt(); // 恢复中断标记，避免吞掉中断信号。
            throw exception; // 将中断异常继续抛给调用方。
        } finally { // 无论回调成功或失败都尝试进行安全解锁。
            if (locked && lock.isHeldByCurrentThread()) { // 仅当当前线程确实持有锁时执行解锁。
                lock.unlock(); // 释放当前线程持有的分布式锁。
            } // 结束锁持有状态判断分支。
        } // 结束资源清理区。
    } // 结束分布式锁执行模板。

    /**
     * 获取字符串值类型 Bucket。
     *
     * <p>Redis 数据类型：String。统一在这里添加 key 前缀和 {@link StringCodec}，
     * 是为了避免各业务方法重复拼接 key，并保证数据能以普通字符串形式被其他客户端读取。
     *
     * @param key 已添加业务分类的相对 key
     * @return 字符串 Bucket
     */
    private RBucket<String> bucket(String key) { // 接收相对 key 并返回 String 操作对象。
        return redissonClient.getBucket(KEY_PREFIX + key, StringCodec.INSTANCE); // 添加统一前缀并使用字符串编解码器。
    } // 结束 Bucket 工厂方法。

    /**
     * 获取用户资料 Hash。
     *
     * <p>Redis 数据类型：Hash，对应 Redisson 的 {@link RMap}。
     * 集中构造用户资料 key 可以保证命名一致，String codec 则避免使用 Java 专属二进制序列化格式。
     *
     * @param userId 用户唯一标识
     * @return 用户资料 Hash
     */
    private RMap<String, String> userProfile(String userId) { // 接收用户标识并返回 Hash 操作对象。
        return redissonClient.getMap( // 从 Redisson 客户端获取用户资料 Map。
                KEY_PREFIX + "user:" + requireText(userId, "userId"), // 构造并校验用户资料 key。
                StringCodec.INSTANCE); // 使用字符串编解码器保存字段和值。
    } // 结束用户资料 Hash 工厂方法。

    /**
     * 获取字符串 List。
     *
     * <p>Redis 数据类型：List，对应 Redisson 的 {@link RList}。
     * 通过工厂方法统一 key 前缀和编解码器，避免调用方因配置不一致而读不到同一份数据。
     *
     * @param key List 相对 key
     * @return 字符串 List
     */
    private RList<String> list(String key) { // 接收相对 key 并返回 List 操作对象。
        return redissonClient.getList(KEY_PREFIX + key, StringCodec.INSTANCE); // 添加统一前缀并使用字符串编解码器。
    } // 结束 List 工厂方法。

    /**
     * 获取字符串 Set。
     *
     * <p>Redis 数据类型：Set，对应 Redisson 的 {@link RSet}。
     * 统一创建方式可以复用成员去重语义，同时保持 key 和字符串编码规则一致。
     *
     * @param key Set 相对 key
     * @return 字符串 Set
     */
    private RSet<String> set(String key) { // 接收相对 key 并返回 Set 操作对象。
        return redissonClient.getSet(KEY_PREFIX + key, StringCodec.INSTANCE); // 添加统一前缀并使用字符串编解码器。
    } // 结束 Set 工厂方法。

    /**
     * 获取排行榜 Sorted Set。
     *
     * <p>Redis 数据类型：Sorted Set，对应 Redisson 的 {@link RScoredSortedSet}。
     * 在此集中添加排行榜命名空间，避免不同排行榜与其他 Set 类型的 key 发生冲突。
     *
     * @param board 排行榜名称
     * @return 带分数的有序集合
     */
    private RScoredSortedSet<String> leaderboard(String board) { // 接收排行榜名称并返回有序集合。
        return redissonClient.getScoredSortedSet( // 从 Redisson 客户端获取 Sorted Set。
                KEY_PREFIX + "leaderboard:" + requireText(board, "board"), // 构造并校验排行榜 key。
                StringCodec.INSTANCE); // 使用字符串编解码器保存成员。
    } // 结束排行榜工厂方法。

    /**
     * 获取用户活跃 Bitmap。
     *
     * <p>Redis 数据类型：Bitmap。Bitmap 实际建立在 Redis String 的位操作之上，
     * 单独封装是为了清晰表达按 bit 保存布尔状态的用途，并统一添加项目 key 前缀。
     *
     * @param key Bitmap 相对 key
     * @return Bitmap
     */
    private RBitSet bitmap(String key) { // 接收相对 key 并返回 Bitmap 操作对象。
        return redissonClient.getBitSet(KEY_PREFIX + key); // 添加统一前缀并获取位集合。
    } // 结束 Bitmap 工厂方法。

    /**
     * 获取活动 UV HyperLogLog。
     *
     * <p>Redis 数据类型：HyperLogLog，对应 Redisson 的 {@link RHyperLogLog}。
     * 使用独立的 {@code uv:} 命名空间可以避免与精确计数器混淆，并统一访客 ID 的字符串编码。
     *
     * @param event 活动或页面名称
     * @return HyperLogLog 计数器
     */
    private RHyperLogLog<String> visitors(String event) { // 接收活动名称并返回 UV 计数器。
        return redissonClient.getHyperLogLog( // 从 Redisson 客户端获取 HyperLogLog。
                KEY_PREFIX + "uv:" + requireText(event, "event"), // 构造并校验活动 UV key。
                StringCodec.INSTANCE); // 使用字符串编解码器保存访客标识。
    } // 结束 HyperLogLog 工厂方法。

    /**
     * 获取门店 Geo 集合。
     *
     * <p>Redis 数据类型：Geo，对应 Redisson 的 {@link RGeo}。
     * 统一使用 {@code geo:} 前缀和字符串门店 ID，便于复用附近查询并防止 key 类型冲突。
     *
     * @param key 地理集合相对 key
     * @return Geo 集合
     */
    private RGeo<String> geo(String key) { // 接收相对 key 并返回 Geo 操作对象。
        return redissonClient.getGeo( // 从 Redisson 客户端获取 Geo 集合。
                KEY_PREFIX + "geo:" + requireText(key, "geoKey"), // 构造并校验 Geo key。
                StringCodec.INSTANCE); // 使用字符串编解码器保存门店标识。
    } // 结束 Geo 工厂方法。

    /**
     * 获取审计 Stream。
     *
     * <p>Redis 数据类型：Stream，对应 Redisson 的 {@link RStream}。
     * 统一 Stream 命名空间和字段编码，可以确保生产者与后续消费者使用相同的数据格式。
     *
     * @param key Stream 相对 key
     * @return 字符串字段 Stream
     */
    private RStream<String, String> stream(String key) { // 接收相对 key 并返回 Stream 操作对象。
        return redissonClient.getStream( // 从 Redisson 客户端获取 Stream。
                KEY_PREFIX + "stream:" + requireText(key, "streamKey"), // 构造并校验 Stream key。
                StringCodec.INSTANCE); // 使用字符串编解码器保存字段和值。
    } // 结束 Stream 工厂方法。

    /**
     * 校验文本参数并返回原值。
     *
     * <p>Redis 数据类型：不直接对应数据类型。Redis 允许很多字符串作为 key，
     * 但业务层拒绝空 key 片段可以防止不同请求意外写入同一个模糊或错误的 key。
     *
     * @param value 待校验文本
     * @param name 参数名
     * @return 非空文本
     */
    private static String requireText(String value, String name) { // 接收待校验文本和参数名。
        if (value == null || value.isBlank()) { // 判断文本是否为 null、空串或纯空白。
            throw new IllegalArgumentException(name + " must not be blank"); // 抛出包含参数名的校验异常。
        } // 结束文本合法性判断分支。
        return value; // 返回已通过校验的原始文本。
    } // 结束文本校验方法。

    /**
     * 校验时间参数必须为正数。
     *
     * <p>Redis 数据类型：不直接对应数据类型。该校验用于 TTL 和锁等待时间，
     * 提前拒绝零值或负值可以避免产生立即过期的缓存或语义不明确的锁等待行为。
     *
     * @param value 待校验时长
     * @param name 参数名
     */
    private static void requirePositive(Duration value, String name) { // 接收待校验时长和参数名。
        Objects.requireNonNull(value, name); // 拒绝空的时长参数。
        if (value.isZero() || value.isNegative()) { // 判断时长是否为零或负数。
            throw new IllegalArgumentException(name + " must be positive"); // 抛出包含参数名的校验异常。
        } // 结束时长合法性判断分支。
    } // 结束时长校验方法。
} // 结束 RedisOperationsServiceImpl 类。
