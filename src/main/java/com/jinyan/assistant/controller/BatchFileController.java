package com.jinyan.assistant.controller;

import com.jinyan.assistant.entity.BatchFileWriteRequest;
import com.jinyan.assistant.entity.BatchFileWriteResult;
import com.jinyan.assistant.service.BatchFileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供文件数据多线程批量写入接口。 */
@RestController
@RequestMapping("/files")
public class BatchFileController {

    private final BatchFileService batchFileService;

    /**
     * 创建批量文件控制器。
     *
     * @param batchFileService 批量文件写入服务
     */
    public BatchFileController(BatchFileService batchFileService) {
        this.batchFileService = batchFileService;
    }

    /**
     * 将请求中的文件数据分批并行写入 {@code jinyan.tb_file_info}。
     *
     * <p>全部成功时返回 200；部分或全部批次失败时返回 207，并在 {@code items} 中标明
     * 每条数据的结果。请求参数本身不合法时由全局异常处理器返回 400。
     *
     * @param request 待批量写入的文件列表
     * @return 批次统计和逐条写入结果
     */
    @PostMapping("/batch")
    public ResponseEntity<BatchFileWriteResult> writeFiles(
            @RequestBody BatchFileWriteRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        BatchFileWriteResult result = batchFileService.writeFiles(request.files());
        HttpStatus status = result.completed() ? HttpStatus.OK : HttpStatus.MULTI_STATUS;
        return ResponseEntity.status(status).body(result);
    }
}
