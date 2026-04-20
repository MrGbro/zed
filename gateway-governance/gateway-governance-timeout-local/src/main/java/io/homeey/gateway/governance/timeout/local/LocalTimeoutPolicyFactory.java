package io.homeey.gateway.governance.timeout.local;

import io.homeey.gateway.governance.api.FailureMode;
import io.homeey.gateway.governance.api.PolicyFactory;
import io.homeey.gateway.governance.api.TimeoutPolicy;

import java.util.Map;

public final class LocalTimeoutPolicyFactory implements PolicyFactory<TimeoutPolicy> {
    @Override
    public String ability() {
        return TimeoutPolicy.ABILITY;
    }

    @Override
    public TimeoutPolicy create(Map<String, Object> entries) {
        boolean enabled = bool(entries, "governance.timeout.enabled", false);
        FailureMode failureMode = FailureMode.parse(str(entries, "governance.timeout.failureMode", "fail-open"), FailureMode.FAIL_OPEN);
        long durationMillis = longValue(entries, "governance.timeout.durationMillis", 0L);
        if (!enabled || durationMillis <= 0L) {
            return TimeoutPolicy.defaultPolicy();
        }
        return new TimeoutPolicy(true, failureMode, durationMillis);
    }

    private boolean bool(Map<String, Object> source, String key, boolean fallback) {
        if (source == null) {
            return fallback;
        }
        Object value = source.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private long longValue(Map<String, Object> source, String key, long fallback) {
        if (source == null) {
            return fallback;
        }
        Object value = source.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value).trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private String str(Map<String, Object> source, String key, String fallback) {
        if (source == null) {
            return fallback;
        }
        Object value = source.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }
}
