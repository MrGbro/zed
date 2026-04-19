package io.homeey.gateway.governance.local;

import io.homeey.gateway.governance.api.CircuitBreakerPolicy;
import io.homeey.gateway.governance.api.DegradePolicy;
import io.homeey.gateway.governance.api.GovernancePolicy;
import io.homeey.gateway.governance.api.GovernancePolicyParser;
import io.homeey.gateway.governance.api.RateLimitPolicy;
import io.homeey.gateway.governance.api.RetryPolicy;
import io.homeey.gateway.governance.api.TimeoutPolicy;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class LocalGovernancePolicyParser implements GovernancePolicyParser {
    @Override
    public GovernancePolicy parse(Map<String, Object> entries) {
        Map<String, Object> source = entries == null ? Map.of() : entries;
        boolean governanceEnabled = bool(source, "governance.enabled", false);
        if (!governanceEnabled) {
            return GovernancePolicy.disabled();
        }

        RateLimitPolicy rate = parseRateLimit(source);
        CircuitBreakerPolicy circuit = parseCircuit(source);
        TimeoutPolicy timeout = parseTimeout(source);
        RetryPolicy retry = parseRetry(source);
        DegradePolicy degrade = parseDegrade(source);
        return new GovernancePolicy(
                true,
                rate,
                circuit,
                timeout,
                retry,
                degrade,
                "local"
        );
    }

    private RateLimitPolicy parseRateLimit(Map<String, Object> source) {
        boolean enabled = bool(source, "governance.ratelimit.enabled", false);
        double qps = dbl(source, "governance.ratelimit.qps", 0D);
        if (!enabled || qps <= 0D) {
            return RateLimitPolicy.disabled();
        }
        int burstDefault = Math.max(1, (int) Math.ceil(qps));
        int burst = integer(source, "governance.ratelimit.burst", burstDefault);
        String key = str(source, "governance.ratelimit.key", "route");
        if (!"route".equalsIgnoreCase(key) && !"ip".equalsIgnoreCase(key)) {
            key = "route";
        }
        return new RateLimitPolicy(true, qps, Math.max(1, burst), key.toLowerCase());
    }

    private CircuitBreakerPolicy parseCircuit(Map<String, Object> source) {
        boolean enabled = bool(source, "governance.circuit.enabled", false);
        if (!enabled) {
            return CircuitBreakerPolicy.disabled();
        }
        int threshold = integer(source, "governance.circuit.failureRateThreshold", 50);
        int minimum = integer(source, "governance.circuit.minimumCalls", 20);
        long openMs = longValue(source, "governance.circuit.openDurationMillis", 10000L);
        int halfOpenMax = integer(source, "governance.circuit.halfOpenMaxCalls", 5);
        if (threshold < 0 || threshold > 100 || minimum < 1 || openMs < 1 || halfOpenMax < 1) {
            return CircuitBreakerPolicy.disabled();
        }
        return new CircuitBreakerPolicy(true, threshold, minimum, openMs, halfOpenMax);
    }

    private TimeoutPolicy parseTimeout(Map<String, Object> source) {
        boolean enabled = bool(source, "governance.timeout.enabled", false);
        long duration = longValue(source, "governance.timeout.durationMillis", 0L);
        if (!enabled || duration < 1L) {
            return TimeoutPolicy.disabled();
        }
        return new TimeoutPolicy(true, duration);
    }

    private RetryPolicy parseRetry(Map<String, Object> source) {
        boolean enabled = bool(source, "governance.retry.enabled", false);
        if (!enabled) {
            return RetryPolicy.disabled();
        }
        int maxAttempts = integer(source, "governance.retry.maxAttempts", 1);
        long backoff = longValue(source, "governance.retry.backoffMillis", 0L);
        Set<Integer> statuses = parseStatuses(str(source, "governance.retry.retryOnStatuses", "502,503,504"));
        if (maxAttempts < 1 || backoff < 0L || statuses.isEmpty()) {
            return RetryPolicy.disabled();
        }
        return new RetryPolicy(true, maxAttempts, backoff, statuses);
    }

    private DegradePolicy parseDegrade(Map<String, Object> source) {
        boolean enabled = bool(source, "governance.degrade.enabled", false);
        if (!enabled) {
            return DegradePolicy.disabled();
        }
        int status = integer(source, "governance.degrade.status", 503);
        String contentType = str(source, "governance.degrade.contentType", "text/plain; charset=UTF-8");
        String body = str(source, "governance.degrade.body", "service degraded");
        return new DegradePolicy(true, status, contentType, body);
    }

    private Set<Integer> parseStatuses(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<Integer> values = new HashSet<>();
        String[] parts = raw.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                values.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {
                return Set.of();
            }
        }
        return Set.copyOf(values);
    }

    private boolean bool(Map<String, Object> source, String key, boolean fallback) {
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
        Object value = source.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private long longValue(Map<String, Object> source, String key, long fallback) {
        Object value = source.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private double dbl(Map<String, Object> source, String key, double fallback) {
        Object value = source.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private String str(Map<String, Object> source, String key, String fallback) {
        Object value = source.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return fallback;
        }
        return text;
    }
}
