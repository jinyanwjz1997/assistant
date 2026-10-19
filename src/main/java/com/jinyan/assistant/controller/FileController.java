package com.jinyan.assistant.controller;

import com.jinyan.assistant.entity.FileInfo;
import com.jinyan.assistant.entity.FileSummary;
import com.jinyan.assistant.service.FileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 提供文件列表和原文详情查询接口。 */
@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;

    /**
     * 创建文件控制器。
     *
     * @param fileService 文件服务
     */
    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 查询所有已保存文件的轻量列表。
     *
     * @return 不包含正文的文件摘要列表
     */
    @GetMapping
    public List<FileSummary> listFiles() {
        return fileService.listFiles();
    }

    /**
     * 查询单个文件及其完整原文。
     *
     * @param fileId 文件 ID
     * @return 完整文件信息
     */
    @GetMapping("/{fileId}")
    public FileInfo getFile(@PathVariable String fileId) {
        return fileService.getFile(fileId);
    }
}
