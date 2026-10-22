package com.jinyan.assistant.entity;

import java.util.List;

/**
 * 批量文件写入请求。
 *
 * @param files 待写入文件列表
 */
public record BatchFileWriteRequest(List<BatchFileItem> files) {
}
