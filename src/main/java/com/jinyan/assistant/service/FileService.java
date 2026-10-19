package com.jinyan.assistant.service;

import com.jinyan.assistant.entity.FileInfo;
import com.jinyan.assistant.entity.FileSummary;

import java.util.List;

public interface FileService {

    /**
     * 保存文件原文。
     *
     * @param fileName 文件名
     * @param fileContext 文件正文
     * @return 已保存文件
     */
    FileInfo saveFile(String fileName, String fileContext);

    /**
     * 根据 ID 查询完整文件。
     *
     * @param fileId 文件 ID
     * @return 完整文件信息
     * @throws IllegalArgumentException 当文件 ID 为空或文件不存在时抛出
     */
    FileInfo getFile(String fileId);

    /**
     * 查询文件列表所需的轻量信息。
     *
     * @return 文件摘要列表
     */
    List<FileSummary> listFiles();
}
