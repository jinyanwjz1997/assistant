package com.jinyan.assistant.controller;

import com.jinyan.assistant.entity.BatchFileItem;
import com.jinyan.assistant.entity.BatchFileItemResult;
import com.jinyan.assistant.entity.BatchFileWriteRequest;
import com.jinyan.assistant.entity.BatchFileWriteResult;
import com.jinyan.assistant.service.BatchFileService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BatchFileControllerTest {

    @Test
    void shouldReturnOkWhenEveryItemSucceeded() {
        BatchFileController controller = new BatchFileController(files -> result(0));

        ResponseEntity<BatchFileWriteResult> response = controller.writeFiles(request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldReturnMultiStatusWhenOneBatchFailed() {
        BatchFileService service = files -> result(1);
        BatchFileController controller = new BatchFileController(service);

        ResponseEntity<BatchFileWriteResult> response = controller.writeFiles(request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MULTI_STATUS);
    }

    /**
     * 创建最小合法请求。
     *
     * @return 批量写入请求
     */
    private BatchFileWriteRequest request() {
        return new BatchFileWriteRequest(List.of(new BatchFileItem("a.txt", "content")));
    }

    /**
     * 创建控制器状态测试所需的固定结果。
     *
     * @param failedCount 失败记录数
     * @return 固定批量结果
     */
    private BatchFileWriteResult result(int failedCount) {
        boolean success = failedCount == 0;
        return new BatchFileWriteResult(
                1,
                success ? 1 : 0,
                failedCount,
                1,
                1,
                List.of(new BatchFileItemResult(
                        0,
                        success ? "file-1" : "",
                        "a.txt",
                        success,
                        success ? "" : "DATABASE_BATCH_WRITE_FAILED",
                        success ? "写入成功" : "当前批次写入失败，事务已回滚"
                ))
        );
    }
}
