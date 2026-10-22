package com.jinyan.assistant.service.impl;

import com.jinyan.assistant.config.BatchWriteProperties;
import com.jinyan.assistant.entity.BatchFileItem;
import com.jinyan.assistant.entity.BatchFileItemResult;
import com.jinyan.assistant.entity.BatchFileWriteResult;
import com.jinyan.assistant.entity.FileInfo;
import com.jinyan.assistant.service.BatchFilePersistenceService;
import com.jinyan.assistant.service.BatchFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** 默认批量文件写入实现：固定大小分片后在有界线程池中并行提交独立事务。 */
@Service
@Slf4j
public class BatchFileServiceImpl implements BatchFileService {

    private static final int MAX_FILE_NAME_LENGTH = 256;
    private static final String WRITE_FAILED_CODE = "DATABASE_BATCH_WRITE_FAILED";

    private final BatchFilePersistenceService persistenceService;
    private final Executor batchExecutor;
    private final BatchWriteProperties properties;

    /**
     * 创建批量文件写入服务。
     *
     * @param persistenceService 单批次 JPA 持久化服务
     * @param batchExecutor 数据库写入专用线程池
     * @param properties 批次大小和请求容量配置
     */
    public BatchFileServiceImpl(
            BatchFilePersistenceService persistenceService,
            @Qualifier("batchDatabaseExecutor") Executor batchExecutor,
            BatchWriteProperties properties
    ) {
        this.persistenceService = persistenceService;
        this.batchExecutor = batchExecutor;
        this.properties = properties;
        properties.validate();
    }

    /**
     * 校验全部数据、生成文件 ID，然后按配置大小并行提交批次。
     *
     * @param files 待写入的文件数据
     * @return 按请求顺序排列的逐条结果及统计信息
     */
    @Override
    public BatchFileWriteResult writeFiles(List<BatchFileItem> files) {
        long startedAt = System.nanoTime();
        List<PreparedFile> preparedFiles = prepareFiles(files);
        List<List<PreparedFile>> batches = partition(preparedFiles, properties.getBatchSize());

        List<CompletableFuture<BatchOutcome>> futures = new ArrayList<>(batches.size());
        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            int currentBatchIndex = batchIndex;
            List<PreparedFile> batch = batches.get(batchIndex);
            futures.add(CompletableFuture.supplyAsync(
                    () -> persistBatch(currentBatchIndex, batch),
                    batchExecutor
            ));
        }

        List<BatchFileItemResult> itemResults = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(outcome -> outcome.items().stream())
                .sorted(Comparator.comparingInt(BatchFileItemResult::index))
                .toList();
        int successCount = (int) itemResults.stream().filter(BatchFileItemResult::success).count();
        int failedCount = itemResults.size() - successCount;
        long durationMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        log.info("批量文件写入完成, total:{}, success:{}, failed:{}, batches:{}, durationMs:{}",
                files.size(), successCount, failedCount, batches.size(), durationMillis);
        return new BatchFileWriteResult(
                files.size(),
                successCount,
                failedCount,
                batches.size(),
                durationMillis,
                itemResults
        );
    }

    /**
     * 执行一个独立事务批次并将异常转换为逐条失败结果。
     *
     * @param batchIndex 批次下标，从 0 开始
     * @param batch 当前批次数据
     * @return 当前批次全部记录的结果
     */
    private BatchOutcome persistBatch(int batchIndex, List<PreparedFile> batch) {
        try {
            persistenceService.persistBatch(batch.stream().map(PreparedFile::entity).toList());
            return new BatchOutcome(batch.stream()
                    .map(file -> new BatchFileItemResult(
                            file.index(),
                            file.entity().getFileId(),
                            file.entity().getFileName(),
                            true,
                            "",
                            "写入成功"
                    ))
                    .toList());
        } catch (RuntimeException exception) {
            log.error("数据库批次写入失败, batchIndex:{}, batchSize:{}",
                    batchIndex, batch.size(), exception);
            return new BatchOutcome(batch.stream()
                    .map(file -> new BatchFileItemResult(
                            file.index(),
                            "",
                            file.entity().getFileName(),
                            false,
                            WRITE_FAILED_CODE,
                            "当前批次写入失败，事务已回滚"
                    ))
                    .toList());
        }
    }

    /**
     * 在启动并发任务前一次性校验请求并生成实体。
     *
     * @param files 原始请求数据
     * @return 带原始下标和服务端 UUID 的待写入数据
     */
    private List<PreparedFile> prepareFiles(List<BatchFileItem> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("files must not be empty");
        }
        if (files.size() > properties.getMaxRecords()) {
            throw new IllegalArgumentException(
                    "files must not contain more than " + properties.getMaxRecords() + " records"
            );
        }

        List<PreparedFile> prepared = new ArrayList<>(files.size());
        for (int index = 0; index < files.size(); index++) {
            BatchFileItem item = files.get(index);
            if (item == null) {
                throw new IllegalArgumentException("files[" + index + "] must not be null");
            }
            if (!StringUtils.hasText(item.fileName())) {
                throw new IllegalArgumentException("files[" + index + "].fileName must not be blank");
            }
            String fileName = item.fileName().trim();
            if (fileName.length() > MAX_FILE_NAME_LENGTH) {
                throw new IllegalArgumentException(
                        "files[" + index + "].fileName must not exceed 256 characters"
                );
            }
            if (item.fileContext() == null) {
                throw new IllegalArgumentException("files[" + index + "].fileContext must not be null");
            }
            prepared.add(new PreparedFile(
                    index,
                    new FileInfo(UUID.randomUUID().toString(), fileName, item.fileContext())
            ));
        }
        return List.copyOf(prepared);
    }

    /**
     * 将列表按固定大小切分；返回的子列表只用于读取。
     *
     * @param files 全部待写入数据
     * @param batchSize 每批记录数
     * @return 保持原始顺序的批次列表
     */
    private List<List<PreparedFile>> partition(List<PreparedFile> files, int batchSize) {
        List<List<PreparedFile>> batches = new ArrayList<>((files.size() + batchSize - 1) / batchSize);
        for (int start = 0; start < files.size(); start += batchSize) {
            batches.add(files.subList(start, Math.min(files.size(), start + batchSize)));
        }
        return batches;
    }

    /** 带请求下标的待写入实体。 */
    private record PreparedFile(int index, FileInfo entity) {
    }

    /** 单个事务批次的执行结果。 */
    private record BatchOutcome(List<BatchFileItemResult> items) {
    }
}
