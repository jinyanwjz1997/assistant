package com.jinyan.assistant.exception;

import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.redisson.client.RedisException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;

/** 将控制器异常转换为稳定、安全且便于前端处理的 JSON 响应。 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理已经声明状态码和业务错误码的异常。
     *
     * @param exception 业务异常
     * @param request 当前请求
     * @return 统一错误响应
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(
            ApiException exception,
            HttpServletRequest request
    ) {
        return respond(
                exception.getStatus(),
                exception.getCode(),
                exception.getMessage(),
                request,
                Map.of(),
                exception,
                false
        );
    }

    /**
     * 处理业务层显式参数校验失败。
     *
     * @param exception 参数异常
     * @param request 当前请求
     * @return HTTP 400 错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT",
                safeMessage(exception, "请求参数不合法"),
                request,
                Map.of(),
                exception,
                false
        );
    }

    /**
     * 处理参数缺失、类型转换和 JSON 解析错误。
     *
     * @param exception Spring MVC 参数绑定异常
     * @param request 当前请求
     * @return HTTP 400 错误响应
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            ServletRequestBindingException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "请求参数缺失或格式不正确",
                request,
                Map.of(),
                exception,
                false
        );
    }

    /**
     * 处理超过服务端限制的上传文件。
     *
     * @param exception 上传大小异常
     * @param request 当前请求
     * @return HTTP 413 错误响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadTooLarge(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "FILE_TOO_LARGE",
                "上传文件超过服务端允许的大小",
                request,
                Map.of(),
                exception,
                false
        );
    }

    /**
     * 处理 HTTP 媒体类型不受接口支持的情况。
     *
     * @param exception 媒体类型异常
     * @param request 当前请求
     * @return HTTP 415 错误响应
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "请求的媒体类型不受支持",
                request,
                Map.of(),
                exception,
                false
        );
    }

    /**
     * 处理不存在的接口路径。
     *
     * @param exception 静态或控制器资源未找到异常
     * @param request 当前请求
     * @return HTTP 404 错误响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "请求的资源不存在",
                request,
                Map.of(),
                exception,
                false
        );
    }

    /**
     * 处理不受接口支持的 HTTP 方法。
     *
     * @param exception 请求方法异常
     * @param request 当前请求
     * @return HTTP 405 错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "当前接口不支持该请求方法",
                request,
                Map.of(),
                exception,
                false
        );
    }

    /**
     * 处理文档读取和解析错误，不向客户端泄露服务器文件路径或底层堆栈。
     *
     * @param exception 文件处理异常
     * @param request 当前请求
     * @return HTTP 422 错误响应
     */
    @ExceptionHandler({IOException.class, UncheckedIOException.class})
    public ResponseEntity<ApiErrorResponse> handleFileProcessing(
            Exception exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "FILE_PROCESSING_FAILED",
                "文件读取或解析失败",
                request,
                Map.of(),
                exception,
                true
        );
    }

    /**
     * 处理上游大模型限流。
     *
     * @param exception 模型限流异常
     * @param request 当前请求
     * @return HTTP 503 错误响应
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleAiRateLimit(
            RateLimitException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_RATE_LIMITED",
                "大模型服务繁忙，请稍后重试",
                request,
                Map.of(),
                exception,
                true
        );
    }

    /**
     * 处理上游大模型超时。
     *
     * @param exception 模型超时异常
     * @param request 当前请求
     * @return HTTP 504 错误响应
     */
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ApiErrorResponse> handleAiTimeout(
            TimeoutException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.GATEWAY_TIMEOUT,
                "AI_TIMEOUT",
                "大模型响应超时，请稍后重试",
                request,
                Map.of(),
                exception,
                true
        );
    }

    /**
     * 处理除限流和超时外的模型服务异常。
     *
     * @param exception LangChain4j 异常
     * @param request 当前请求
     * @return HTTP 502 错误响应
     */
    @ExceptionHandler(LangChain4jException.class)
    public ResponseEntity<ApiErrorResponse> handleAiService(
            LangChain4jException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.BAD_GATEWAY,
                "AI_SERVICE_ERROR",
                "大模型服务调用失败",
                request,
                Map.of(),
                exception,
                true
        );
    }

    /**
     * 处理 PostgreSQL 和 Redis 暂时不可用的情况。
     *
     * @param exception 基础设施异常
     * @param request 当前请求
     * @return HTTP 503 错误响应
     */
    @ExceptionHandler({DataAccessException.class, RedisException.class})
    public ResponseEntity<ApiErrorResponse> handleInfrastructureUnavailable(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.SERVICE_UNAVAILABLE,
                "INFRASTRUCTURE_UNAVAILABLE",
                "数据库或缓存服务暂时不可用",
                request,
                Map.of(),
                exception,
                true
        );
    }

    /**
     * 兜底处理未预期异常，仅向客户端返回安全信息，完整堆栈记录在服务端日志中。
     *
     * @param exception 未预期异常
     * @param request 当前请求
     * @return HTTP 500 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "服务器处理请求时发生错误",
                request,
                Map.of(),
                exception,
                true
        );
    }

    /**
     * 构造统一响应并使用 requestId 记录可关联日志。
     *
     * @param status HTTP 状态
     * @param code 业务错误码
     * @param message 用户可见信息
     * @param request 当前请求
     * @param details 补充错误详情
     * @param exception 原始异常
     * @param includeStackTrace 是否记录完整堆栈
     * @return 统一错误响应
     */
    private ResponseEntity<ApiErrorResponse> respond(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> details,
            Exception exception,
            boolean includeStackTrace
    ) {
        String requestId = UUID.randomUUID().toString();
        if (includeStackTrace) {
            log.error("请求处理失败, requestId:{}, method:{}, path:{}, code:{}",
                    requestId, request.getMethod(), request.getRequestURI(), code, exception);
        } else {
            log.warn("请求未成功, requestId:{}, method:{}, path:{}, code:{}, reason:{}",
                    requestId, request.getMethod(), request.getRequestURI(), code, exception.getMessage());
        }
        return ResponseEntity.status(status).body(ApiErrorResponse.of(
                status.value(),
                code,
                message,
                request.getRequestURI(),
                requestId,
                details
        ));
    }

    /**
     * 获取安全的异常文本。
     *
     * @param exception 原始异常
     * @param fallback 无可用文本时的默认信息
     * @return 非空错误信息
     */
    private String safeMessage(Exception exception, String fallback) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? fallback
                : exception.getMessage();
    }
}
