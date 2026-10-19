package com.jinyan.assistant.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RedissonConfig {

    /** 创建单机 Redisson 客户端；生产环境可替换为集群或哨兵连接器。
     * @param redisUrl Redis 连接地址
     * @return Redisson 客户端
     */
    @Bean(destroyMethod = "shutdown")
    @Primary
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.url:redis://127.0.0.1:6379}") String redisUrl
    ) {
        Config config = new Config();
        config.useSingleServer()
                .setAddress(redisUrl)
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(8);
        return Redisson.create(config);
    }
}
