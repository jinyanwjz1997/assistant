package com.jinyan.assistant.service;

import com.jinyan.assistant.entity.BatchFileItem;
import com.jinyan.assistant.entity.BatchFileWriteResult;

import java.util.List;

/** 管理文件数据校验、分批、并行写入和结果汇总。 */
public interface BatchFileService {

    /**
     * 将文件数据分批后使用受控线程池并行写入数据库。
     *
     * <p>每个批次使用独立事务。单批失败会整体回滚，但不会回滚已经由其他线程成功提交的批次。
     *
     * @param files 待写入的文件数据
     * @return 包含每条数据成功或失败状态的汇总结果
     * @throws IllegalArgumentException 当列表为空、超过上限或任一字段不合法时抛出
     */
    BatchFileWriteResult writeFiles(List<BatchFileItem> files);
}
