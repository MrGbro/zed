package io.homeey.gateway.governance.degrade.local;

import io.homeey.gateway.governance.api.DegradePolicy;
import io.homeey.gateway.governance.api.FailureMode;
import io.homeey.gateway.governance.api.PolicyFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class LocalDegradePolicyFactory implements PolicyFactory<DegradePolicy> {
    @Override
    public String ability() {
        return DegradePolicy.ABILITY;
    }

    @Override
    public DegradePolicy create(Map<String, Object> entries) {
        boolean enabled = bool(entries, "governance.degrade.enabled", false);
        FailureMode failureMode = FailureMode.parse(str(entries, "governance.degrade.failureMode", "fail-open"), FailureMode.FAIL_OPEN);
        int status = integer(entries, "governance.degrade.status", 503);
        String contentType = str(entries, "governance.degrade.contentType", "text/plain; charset=UTF-8");
        String body = str(entries, "governance.degrade.body", "service degraded");
        Set<String> triggerOn = parseTriggerOn(str(entries, "governance.degrade.triggerOn", "rate_limited,circuit_open,timeout,retry_exhausted,governance_error"));
        if (!enabled) {
            return DegradePolicy.defaultPolicy();
        }
        return new DegradePolicy(true, failureMode, status, contentType, body, triggerOn);
    }

    private Set<String> parseTriggerOn(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        String[] parts = raw.split(",");
        Set<String> values = new HashSet<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            values.add(part.trim().toLowerCase());
        }
        return Set.copyOf(values);
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
