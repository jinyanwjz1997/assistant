package com.jinyan.assistant.repository;

import com.jinyan.assistant.entity.FileInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileInfoRepository extends JpaRepository<FileInfo, String> {
}
