package io.homeey.gateway.common.error;

/**
 * 网关错误记录类，封装错误的详细信息。
 * <p>
 * 包含错误码、错误分类、HTTP状态码、是否可重试、错误消息和原因ID等信息，
 * 用于统一的错误处理和响应格式化。
 * </p>
 *
 * @param code       错误码，用于唯一标识错误类型
 * @param category   错误分类，参见 {@link ErrorCategory}
 * @param httpStatus HTTP状态码，用于生成HTTP响应
 * @param retryable  是否可重试，指示客户端是否可以安全地重试请求
 * @param message    错误消息，人类可读的错误描述
 * @param causeId    原因ID，用于追踪错误的根本原因或关联日志
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public record GatewayError(
        String code,
        ErrorCategory category,
        int httpStatus,
        boolean retryable,
        String message,
        String causeId
) {
}