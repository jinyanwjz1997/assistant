package com.jinyan.assistant.service.impl;

import com.jinyan.assistant.entity.FileInfo;
import com.jinyan.assistant.service.BatchFilePersistenceService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 使用线程绑定的 JPA EntityManager 执行单批数据库写入。 */
@Service
public class JpaBatchFilePersistenceService implements BatchFilePersistenceService {

    private final EntityManager entityManager;

    /**
     * 创建 JPA 批次持久化服务。
     *
     * @param entityManager Spring 提供的线程绑定 EntityManager 代理
     */
    public JpaBatchFilePersistenceService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * 在新事务中写入整个批次。
     *
     * <p>该方法由独立 Spring Bean 暴露，异步线程通过代理调用时 {@code @Transactional}
     * 才会生效。不能把它写成协调服务中的私有方法，否则属于同类内部调用，事务代理会失效。
     *
     * @param files 已完成校验并分配 ID 的文件实体
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistBatch(List<FileInfo> files) {
        for (FileInfo file : files) {
            entityManager.persist(file);
        }
        // 在事务返回前触发 SQL，确保数据库异常能被当前批次捕获并正确汇总。
        entityManager.flush();
        entityManager.clear();
    }
}
