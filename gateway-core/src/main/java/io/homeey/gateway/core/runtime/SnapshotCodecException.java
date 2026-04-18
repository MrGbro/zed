package io.homeey.gateway.core.runtime;

public final class SnapshotCodecException extends RuntimeException {
    private final String code;
    private final String field;

    public SnapshotCodecException(String code, String message) {
        this(code, message, null, null);
    }

    public SnapshotCodecException(String code, String message, String field) {
        this(code, message, field, null);
    }

    public SnapshotCodecException(String code, String message, Throwable cause) {
        this(code, message, null, cause);
    }

    public SnapshotCodecException(String code, String message, String field, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.field = field;
    }

    public String code() {
        return code;
    }

    public String field() {
        return field;
    }
}
