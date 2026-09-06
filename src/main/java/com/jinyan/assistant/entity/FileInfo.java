package com.jinyan.assistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "tb_file_info", schema = "jinyan")
public class FileInfo {

    @Id
    @Column(name = "file_id", nullable = false, length = 256, updatable = false)
    private String fileId;

    @Column(name = "file_name", length = 256)
    private String fileName;

    @Column(name = "file_context", columnDefinition = "text")
    private String fileContext;

    protected FileInfo() {
    }

    public FileInfo(String fileId, String fileName, String fileContext) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileContext = fileContext;
    }

}
