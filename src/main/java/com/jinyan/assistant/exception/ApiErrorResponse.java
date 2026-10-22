package com.jinyan.assistant.exception;

import java.time.Instant;
import java.util.Map;

/**
 * 所有非流式 HTTP 接口统一使用的错误响应。
 *
 * @param timestamp 错误发生时间
 * @param status HTTP 状态码
 * @param code 稳定的业务错误码，供前端判断错误类型
 * @param message 可直接展示给用户的错误信息
 * @param path 发生错误的请求路径
 * @param requestId 本次错误的唯一标识，用于关联服务端日志
 * @param details 字段级错误等补充信息，没有时为空 Map
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String requestId,
        Map<String, String> details
) {

    /**
     * 创建不可变的统一错误响应。
     *
     * @param status HTTP 状态码
     * @param code 业务错误码
     * @param message 用户可见错误信息
     * @param path 请求路径
     * @param requestId 错误请求 ID
     * @param details 补充错误详情，可以为空
     * @return 统一错误响应
     */
    public static ApiErrorResponse of(
            int status,
            String code,
            String message,
            String path,
            String requestId,
            Map<String, String> details
    ) {
        return new ApiErrorResponse(
                Instant.now(),
                status,
                code,
                message,
                path,
                requestId,
                details == null ? Map.of() : Map.copyOf(details)
        );
    }
}
