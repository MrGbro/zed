package io.homeey.gateway.core.runtime;

/**
 * 快照编解码异常，在路由快照序列化或反序列化失败时抛出。
 * <p>
 * 包含错误码和字段信息，便于定位编解码错误的具体位置。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public final class SnapshotCodecException extends RuntimeException {
    private final String code;
    private final String field;

    /**
     * 构造快照编解码异常。
     *
     * @param code    错误码
     * @param message 错误消息
     */
    public SnapshotCodecException(String code, String message) {
        this(code, message, null, null);
    }

    /**
     * 构造快照编解码异常。
     *
     * @param code    错误码
     * @param message 错误消息
     * @param field   出错的字段名
     */
    public SnapshotCodecException(String code, String message, String field) {
        this(code, message, field, null);
    }

    /**
     * 构造快照编解码异常。
     *
     * @param code    错误码
     * @param message 错误消息
     * @param cause   根本原因
     */
    public SnapshotCodecException(String code, String message, Throwable cause) {
        this(code, message, null, cause);
    }

    /**
     * 构造快照编解码异常。
     *
     * @param code    错误码
     * @param message 错误消息
     * @param field   出错的字段名
     * @param cause   根本原因
     */
    public SnapshotCodecException(String code, String message, String field, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.field = field;
    }

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    public String code() {
        return code;
    }

    /**
     * 获取出错的字段名。
     *
     * @return 字段名，可能为null
     */
    public String field() {
        return field;
    }
}
