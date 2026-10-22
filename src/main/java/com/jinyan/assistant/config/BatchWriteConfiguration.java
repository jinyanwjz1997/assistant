package com.jinyan.assistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/** 为数据库批量写入提供有界且可优雅关闭的专用线程池。 */
@Configuration
public class BatchWriteConfiguration {

    /**
     * 创建数据库写入线程池。
     *
     * <p>线程数固定，避免请求量直接转化为数据库连接数。队列满时使用调用方线程执行，
     * 通过降低请求处理速度形成背压，而不是继续无限堆积任务。
     *
     * @param properties 批量写入配置
     * @return 数据库批量写入执行器
     */
    @Bean("batchDatabaseExecutor")
    public ThreadPoolTaskExecutor batchDatabaseExecutor(BatchWriteProperties properties) {
        properties.validate();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getParallelism());
        executor.setMaxPoolSize(properties.getParallelism());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("database-batch-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
