package io.homeey.gateway.core.error;

import io.homeey.gateway.common.error.ErrorCategory;

import java.util.Map;

public final class ErrorMapper {

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
