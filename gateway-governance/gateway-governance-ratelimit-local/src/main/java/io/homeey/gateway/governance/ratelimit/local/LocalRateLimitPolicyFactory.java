package io.homeey.gateway.governance.ratelimit.local;

import io.homeey.gateway.governance.api.FailureMode;
import io.homeey.gateway.governance.api.PolicyFactory;
import io.homeey.gateway.governance.api.RateLimitPolicy;

import java.util.Map;

public final class LocalRateLimitPolicyFactory implements PolicyFactory<RateLimitPolicy> {
    @Override
    public String ability() {
        return RateLimitPolicy.ABILITY;
    }

    @Override
    public RateLimitPolicy create(Map<String, Object> entries) {
        boolean enabled = bool(entries, "governance.ratelimit.enabled", false);
        FailureMode failureMode = FailureMode.parse(str(entries, "governance.ratelimit.failureMode", "fail-close"), FailureMode.FAIL_CLOSE);
        double qps = dbl(entries, "governance.ratelimit.qps", 0D);
        int burstDefault = Math.max(1, (int) Math.ceil(qps <= 0D ? 1D : qps));
        int burst = integer(entries, "governance.ratelimit.burst", burstDefault);
        String provider = str(entries, "governance.ratelimit.provider", "local");
        String keyType = str(entries, "governance.ratelimit.keyType", "route");
        String keyHeader = str(entries, "governance.ratelimit.keyHeader", "");
        if (!enabled || qps <= 0D) {
            return RateLimitPolicy.defaultPolicy();
        }
        return new RateLimitPolicy(true, failureMode, qps, burst, provider, keyType, keyHeader);
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

    private double dbl(Map<String, Object> source, String key, double fallback) {
        if (source == null) {
            return fallback;
        }
        Object value = source.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value).trim());
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
