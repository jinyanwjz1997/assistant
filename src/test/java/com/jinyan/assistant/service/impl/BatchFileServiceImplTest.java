package com.jinyan.assistant.service.impl;

import com.jinyan.assistant.config.BatchWriteProperties;
import com.jinyan.assistant.entity.BatchFileItem;
import com.jinyan.assistant.entity.BatchFileItemResult;
import com.jinyan.assistant.entity.BatchFileWriteResult;
import com.jinyan.assistant.entity.FileInfo;
import com.jinyan.assistant.service.BatchFilePersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchFileServiceImplTest {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutDownExecutor() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    void shouldPartitionAndWriteBatchesConcurrently() {
        RecordingPersistenceService persistence = new RecordingPersistenceService(false);
        BatchFileServiceImpl service = new BatchFileServiceImpl(
                persistence,
                executor,
                properties(2, 10)
        );

        BatchFileWriteResult result = service.writeFiles(List.of(
                item("a.txt"),
                item("b.txt"),
                item("c.txt"),
                item("d.txt"),
                item("e.txt")
        ));

        assertThat(result.totalCount()).isEqualTo(5);
        assertThat(result.successCount()).isEqualTo(5);
        assertThat(result.failedCount()).isZero();
        assertThat(result.batchCount()).isEqualTo(3);
        assertThat(result.completed()).isTrue();
        assertThat(result.items()).extracting(BatchFileItemResult::index)
                .containsExactly(0, 1, 2, 3, 4);
        assertThat(result.items()).extracting(BatchFileItemResult::fileId)
                .allMatch(fileId -> fileId != null && !fileId.isBlank());
        assertThat(persistence.threadNames).hasSizeGreaterThanOrEqualTo(2);
        assertThat(persistence.maximumConcurrency).hasValueGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldRollbackOnlyFailedBatchAndKeepInputOrder() {
        RecordingPersistenceService persistence = new RecordingPersistenceService(true);
        BatchFileServiceImpl service = new BatchFileServiceImpl(
                persistence,
                executor,
                properties(2, 10)
        );

        BatchFileWriteResult result = service.writeFiles(List.of(
                item("a.txt"),
                item("b.txt"),
                item("fail.txt"),
                item("same-batch.txt"),
                item("e.txt")
        ));

        assertThat(result.successCount()).isEqualTo(3);
        assertThat(result.failedCount()).isEqualTo(2);
        assertThat(result.completed()).isFalse();
        assertThat(result.items()).extracting(BatchFileItemResult::success)
                .containsExactly(true, true, false, false, true);
        assertThat(result.items().get(2).errorCode()).isEqualTo("DATABASE_BATCH_WRITE_FAILED");
        assertThat(result.items().get(2).fileId()).isEmpty();
        assertThat(result.items().get(3).fileId()).isEmpty();
    }

    @Test
    void shouldRejectInvalidInputBeforeStartingWorkers() {
        RecordingPersistenceService persistence = new RecordingPersistenceService(false);
        BatchFileServiceImpl service = new BatchFileServiceImpl(
                persistence,
                executor,
                properties(2, 2)
        );

        assertThatThrownBy(() -> service.writeFiles(List.of(
                item("a.txt"),
                item("b.txt"),
                item("c.txt")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than 2 records");
        assertThat(persistence.persistedBatches).hasValue(0);
    }

    /**
     * 创建一条测试文件数据。
     *
     * @param fileName 文件名
     * @return 测试数据
     */
    private BatchFileItem item(String fileName) {
        return new BatchFileItem(fileName, "content of " + fileName);
    }

    /**
     * 创建测试批量配置。
     *
     * @param batchSize 每批记录数
     * @param maxRecords 单请求最大记录数
     * @return 测试配置
     */
    private BatchWriteProperties properties(int batchSize, int maxRecords) {
        BatchWriteProperties properties = new BatchWriteProperties();
        properties.setBatchSize(batchSize);
        properties.setMaxRecords(maxRecords);
        return properties;
    }

    /** 记录线程和并发度，并可按文件名模拟整批事务失败。 */
    private static final class RecordingPersistenceService implements BatchFilePersistenceService {

        private final boolean failNamedBatch;
        private final Set<String> threadNames = ConcurrentHashMap.newKeySet();
        private final AtomicInteger activeCalls = new AtomicInteger();
        private final AtomicInteger maximumConcurrency = new AtomicInteger();
        private final AtomicInteger persistedBatches = new AtomicInteger();

        private RecordingPersistenceService(boolean failNamedBatch) {
            this.failNamedBatch = failNamedBatch;
        }

        @Override
        public void persistBatch(List<FileInfo> files) {
            threadNames.add(Thread.currentThread().getName());
            int active = activeCalls.incrementAndGet();
            maximumConcurrency.accumulateAndGet(active, Math::max);
            try {
                Thread.sleep(50);
                if (failNamedBatch && files.stream()
                        .anyMatch(file -> "fail.txt".equals(file.getFileName()))) {
                    throw new IllegalStateException("simulated database failure");
                }
                persistedBatches.incrementAndGet();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("test worker interrupted", exception);
            } finally {
                activeCalls.decrementAndGet();
            }
        }
    }
}
