package com.jinyan.assistant.controller;

import com.jinyan.assistant.entity.FileInfo;
import com.jinyan.assistant.service.FileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/{fileId}")
    public FileInfo getFile(@PathVariable String fileId) {
        return fileService.getFile(fileId);
    }
}
