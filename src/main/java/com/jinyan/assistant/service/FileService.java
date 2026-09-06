package com.jinyan.assistant.service;

import com.jinyan.assistant.entity.FileInfo;

public interface FileService {

    FileInfo saveFile(String fileName, String fileContext);

    FileInfo getFile(String fileId);
}
