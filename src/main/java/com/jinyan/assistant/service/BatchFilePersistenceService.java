package com.jinyan.assistant.service;

import com.jinyan.assistant.entity.FileInfo;

import java.util.List;

/** 在独立数据库事务中持久化一个文件批次。 */
public interface BatchFilePersistenceService {

    /**
     * 原子写入一个批次；任一记录失败时回滚整个批次。
     *
     * @param files 已完成校验并分配 ID 的文件实体
     */
    void persistBatch(List<FileInfo> files);
}
