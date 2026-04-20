package io.homeey.gateway.governance.api;

public enum FailureMode {
    FAIL_OPEN,
    FAIL_CLOSE;

    public static FailureMode parse(String raw, FailureMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        if ("fail-close".equalsIgnoreCase(raw) || "fail_close".equalsIgnoreCase(raw)) {
            return FAIL_CLOSE;
        }
        if ("fail-open".equalsIgnoreCase(raw) || "fail_open".equalsIgnoreCase(raw)) {
            return FAIL_OPEN;
        }
        return fallback;
    }
}
