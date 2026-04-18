package io.homeey.gateway.core.error;

import io.homeey.gateway.common.error.ErrorCategory;

import java.util.Map;

/**
 * 错误映射器，将异常转换为统一的错误响应格式。
 * <p>
 * 该映射器根据异常类型生成包含错误码、分类、状态码和消息的响应Map。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public final class ErrorMapper {

    /**
     * 将异常映射为错误响应。
     *
     * @param throwable 异常对象
     * @param traceId   追踪ID
     * @return 错误响应Map，包含code、category、status、message和headers
     */
    public Map<String, Object> map(Throwable throwable, String traceId) {
        if (throwable instanceof IllegalArgumentException) {
            return Map.of(
                    "code", "GW4001",
                    "category", ErrorCategory.CLIENT_ERROR.name(),
                    "status", 400,
                    "message", throwable.getMessage(),
                    "headers", Map.of("X-Trace-Id", traceId)
            );
        }

        return Map.of(
                "code", "GW5001",
                "category", ErrorCategory.SYSTEM_ERROR.name(),
                "status", 500,
                "message", throwable.getMessage(),
                "headers", Map.of("X-Trace-Id", traceId)
        );
    }
}
