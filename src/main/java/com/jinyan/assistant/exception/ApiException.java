package com.jinyan.assistant.exception;

import org.springframework.http.HttpStatus;

/** 表示状态码和错误码均已明确的可预期业务异常。 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    /**
     * 创建业务异常。
     *
     * @param status 应返回的 HTTP 状态
     * @param code 稳定的业务错误码
     * @param message 用户可见错误信息
     */
    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * 获取 HTTP 状态。
     *
     * @return HTTP 状态
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * 获取业务错误码。
     *
     * @return 业务错误码
     */
    public String getCode() {
        return code;
    }
}
