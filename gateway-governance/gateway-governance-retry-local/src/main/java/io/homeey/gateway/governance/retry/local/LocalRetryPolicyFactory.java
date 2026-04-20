package io.homeey.gateway.governance.retry.local;

import io.homeey.gateway.governance.api.FailureMode;
import io.homeey.gateway.governance.api.PolicyFactory;
import io.homeey.gateway.governance.api.RetryPolicy;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class LocalRetryPolicyFactory implements PolicyFactory<RetryPolicy> {
    @Override
    public String ability() {
        return RetryPolicy.ABILITY;
    }

    @Override
    public RetryPolicy create(Map<String, Object> entries) {
        boolean enabled = bool(entries, "governance.retry.enabled", false);
        FailureMode failureMode = FailureMode.parse(str(entries, "governance.retry.failureMode", "fail-open"), FailureMode.FAIL_OPEN);
        int maxAttempts = integer(entries, "governance.retry.maxAttempts", 1);
        long backoffMillis = longValue(entries, "governance.retry.backoffMillis", 0L);
        Set<Integer> statuses = parseStatuses(str(entries, "governance.retry.retryOnStatuses", "502,503,504"));
        boolean retryOnTimeout = bool(entries, "governance.retry.retryOnTimeout", true);
        if (!enabled) {
            return RetryPolicy.defaultPolicy();
        }
        return new RetryPolicy(true, failureMode, maxAttempts, backoffMillis, statuses, retryOnTimeout);
    }

    private Set<Integer> parseStatuses(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of(502, 503, 504);
        }
        String[] parts = raw.split(",");
        Set<Integer> values = new HashSet<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            try {
                values.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                return Set.of(502, 503, 504);
            }
        }
        return values.isEmpty() ? Set.of(502, 503, 504) : Set.copyOf(values);
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

    private int integer(Map<String, Object> source, String key, int fallback) {
        if (source == null) {
            return fallback;
        }
        Object value = source.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value).trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
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
