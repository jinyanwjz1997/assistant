package com.jinyan.assistant.service.impl;

import com.jinyan.assistant.entity.FileInfo;
import com.jinyan.assistant.repository.FileInfoRepository;
import com.jinyan.assistant.service.FileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {

    private static final int MAX_FILE_NAME_LENGTH = 256;

    private final FileInfoRepository fileInfoRepository;

    public FileServiceImpl(FileInfoRepository fileInfoRepository) {
        this.fileInfoRepository = fileInfoRepository;
    }

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

    @Override
    @Transactional(readOnly = true)
    public FileInfo getFile(String fileId) {
        if (!StringUtils.hasText(fileId)) {
            throw new IllegalArgumentException("fileId must not be blank");
        }
        return fileInfoRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
    }

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
