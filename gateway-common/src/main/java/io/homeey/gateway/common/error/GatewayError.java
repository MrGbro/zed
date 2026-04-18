package io.homeey.gateway.common.error;

public record GatewayError(
        String code,
        ErrorCategory category,
        int httpStatus,
        boolean retryable,
        String message,
        String causeId
) {
}