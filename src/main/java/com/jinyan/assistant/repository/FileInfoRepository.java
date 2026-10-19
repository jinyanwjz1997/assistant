package com.jinyan.assistant.repository;

import com.jinyan.assistant.entity.FileInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FileInfoRepository extends JpaRepository<FileInfo, String> {

    /**
     * 只查询文件列表需要的列，避免把大字段 {@code file_context} 全量加载到内存。
     *
     * @return 按文件名排序的文件摘要投影
     */
    @Query(value = """
            SELECT file_id AS "fileId",
                   file_name AS "fileName",
                   COALESCE(char_length(file_context), 0) AS "characterCount"
            FROM jinyan.tb_file_info
            ORDER BY file_name, file_id
            """, nativeQuery = true)
    List<FileSummaryProjection> findAllSummaries();

    /** 文件列表查询使用的闭合投影。 */
    interface FileSummaryProjection {

        /**
         * 获取文件 ID。
         *
         * @return 文件唯一标识
         */
        String getFileId();

        /**
         * 获取文件名。
         *
         * @return 文件名
         */
        String getFileName();

        /**
         * 获取正文字符数。
         *
         * @return 正文字符数
         */
        Integer getCharacterCount();
    }
}
