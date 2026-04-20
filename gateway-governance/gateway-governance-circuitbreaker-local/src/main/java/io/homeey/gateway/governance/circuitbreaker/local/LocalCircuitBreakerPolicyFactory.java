package io.homeey.gateway.governance.circuitbreaker.local;

import io.homeey.gateway.governance.api.CircuitBreakerPolicy;
import io.homeey.gateway.governance.api.FailureMode;
import io.homeey.gateway.governance.api.PolicyFactory;

import java.util.Map;

public final class LocalCircuitBreakerPolicyFactory implements PolicyFactory<CircuitBreakerPolicy> {
    @Override
    public String ability() {
        return CircuitBreakerPolicy.ABILITY;
    }

    @Override
    public CircuitBreakerPolicy create(Map<String, Object> entries) {
        boolean enabled = bool(entries, "governance.circuitbreaker.enabled", false);
        FailureMode failureMode = FailureMode.parse(str(entries, "governance.circuitbreaker.failureMode", "fail-close"), FailureMode.FAIL_CLOSE);
        int threshold = integer(entries, "governance.circuitbreaker.failureRateThreshold", 50);
        int minimum = integer(entries, "governance.circuitbreaker.minimumCalls", 20);
        long openMs = longValue(entries, "governance.circuitbreaker.openDurationMillis", 10000L);
        int halfOpenMax = integer(entries, "governance.circuitbreaker.halfOpenMaxCalls", 5);
        if (!enabled) {
            return CircuitBreakerPolicy.defaultPolicy();
        }
        return new CircuitBreakerPolicy(true, failureMode, threshold, minimum, openMs, halfOpenMax);
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
