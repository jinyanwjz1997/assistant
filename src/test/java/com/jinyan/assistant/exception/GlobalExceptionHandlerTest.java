package com.jinyan.assistant.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnUnifiedBadRequestResponse() throws Exception {
        mockMvc.perform(get("/test/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("question must not be blank"))
                .andExpect(jsonPath("$.path").value("/test/invalid"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void shouldPreserveBusinessStatusAndCode() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("文件不存在"));
    }

    @Test
    void shouldHideUnexpectedExceptionDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("服务器处理请求时发生错误"));
    }

    @Test
    void shouldHandleMissingRequestParameter() throws Exception {
        mockMvc.perform(get("/test/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /** 仅用于验证全局异常映射的测试控制器。 */
    @RestController
    @RequestMapping("/test")
    private static final class FailureController {

        /**
         * 抛出参数异常。
         *
         * @return 不会正常返回
         */
        @GetMapping("/invalid")
        String invalid() {
            throw new IllegalArgumentException("question must not be blank");
        }

        /**
         * 抛出带状态码的业务异常。
         *
         * @return 不会正常返回
         */
        @GetMapping("/not-found")
        String notFound() {
            throw new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "文件不存在");
        }

        /**
         * 抛出未预期异常。
         *
         * @return 不会正常返回
         */
        @GetMapping("/unexpected")
        String unexpected() {
            throw new IllegalStateException("数据库内部地址不应返回给客户端");
        }

        /**
         * 声明一个必填参数，用于验证 Spring MVC 参数绑定异常。
         *
         * @param value 必填参数
         * @return 原参数
         */
        @GetMapping("/required")
        String required(@RequestParam String value) {
            return value;
        }
    }
}
