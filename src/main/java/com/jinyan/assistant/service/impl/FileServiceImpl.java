package com.jinyan.assistant.service.impl;

import com.jinyan.assistant.entity.FileInfo;
import com.jinyan.assistant.entity.FileSummary;
import com.jinyan.assistant.repository.FileInfoRepository;
import com.jinyan.assistant.service.FileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.List;

/** 使用 JPA 持久化和查询文件原文。 */
@Service
public class FileServiceImpl implements FileService {

    private static final int MAX_FILE_NAME_LENGTH = 256;

    private final FileInfoRepository fileInfoRepository;

    /**
     * 创建文件服务。
     *
     * @param fileInfoRepository 文件 JPA 仓库
     */
    public FileServiceImpl(FileInfoRepository fileInfoRepository) {
        this.fileInfoRepository = fileInfoRepository;
    }

    /**
     * 校验并保存一份文件原文。
     *
     * @param fileName 文件名
     * @param fileContext 文件正文
     * @return 已保存文件
     * @throws IllegalArgumentException 当文件名或正文不合法时抛出
     */
    @Override
    @Transactional
    public FileInfo saveFile(String fileName, String fileContext) {
        validate(fileName, fileContext);

        FileInfo fileInfo = new FileInfo(
                UUID.randomUUID().toString(),
                fileName,
                fileContext
        );
        return fileInfoRepository.save(fileInfo);
    }

    /**
     * 根据 ID 查询完整文件。
     *
     * @param fileId 文件 ID
     * @return 完整文件信息
     * @throws IllegalArgumentException 当文件 ID 为空或文件不存在时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public FileInfo getFile(String fileId) {
        if (!StringUtils.hasText(fileId)) {
            throw new IllegalArgumentException("fileId must not be blank");
        }
        return fileInfoRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
    }

    /**
     * 查询不包含文件正文的轻量列表。
     *
     * @return 按文件名排序的文件摘要
     */
    @Override
    @Transactional(readOnly = true)
    public List<FileSummary> listFiles() {
        return fileInfoRepository.findAllSummaries().stream()
                .map(file -> new FileSummary(
                        file.getFileId(),
                        file.getFileName(),
                        file.getCharacterCount() == null ? 0 : file.getCharacterCount()
                ))
                .toList();
    }

    /**
     * 校验待保存文件参数。
     *
     * @param fileName 文件名
     * @param fileContext 文件正文
     * @throws IllegalArgumentException 当参数不符合持久化约束时抛出
     */
    private void validate(String fileName, String fileContext) {
        if (!StringUtils.hasText(fileName)) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        if (fileName.length() > MAX_FILE_NAME_LENGTH) {
            throw new IllegalArgumentException("fileName must not exceed 256 characters");
        }
        if (fileContext == null) {
            throw new IllegalArgumentException("fileContext must not be null");
        }
    }
}
