package io.homeey.gateway.governance.ratelimit.local;

import io.homeey.gateway.governance.api.GovernanceExecutionContext;
import io.homeey.gateway.governance.api.GovernanceStateStore;
import io.homeey.gateway.governance.api.RateLimitPolicy;
import io.homeey.gateway.governance.api.RateLimitPolicyHandler;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class LocalRateLimitPolicyHandler implements RateLimitPolicyHandler {
    @Override
    public boolean allow(GovernanceExecutionContext context, RateLimitPolicy policy, GovernanceStateStore stateStore) {
        if (!policy.enabled()) {
            return true;
        }
        String key = resolveKey(context, policy);
        TokenBucket bucket = stateStore.computeIfAbsent("ratelimit:" + key, () -> new TokenBucket(policy.burst(), policy.qps()), TokenBucket.class);
        return bucket.tryAcquire();
    }

    private String resolveKey(GovernanceExecutionContext context, RateLimitPolicy policy) {
        if ("ip".equals(policy.keyType())) {
            String forwarded = header(context, "x-forwarded-for");
            if (forwarded != null && !forwarded.isBlank()) {
                return "ip:" + forwarded;
            }
        }
        if ("header".equals(policy.keyType())) {
            String value = header(context, policy.keyHeader());
            if (value != null && !value.isBlank()) {
                return "header:" + policy.keyHeader() + ":" + value;
            }
        }
        return "route:" + (context.routeId() == null ? "" : context.routeId());
    }

    private String header(GovernanceExecutionContext context, String name) {
        if (name == null || name.isBlank() || context.gatewayContext().request() == null) {
            return null;
        }
        Map<String, String> headers = context.gatewayContext().request().headers();
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static final class TokenBucket {
        private final int capacity;
        private final double refillRatePerNano;
        private double tokens;
        private long lastRefillNanos;

        private TokenBucket(int capacity, double qps) {
            this.capacity = Math.max(1, capacity);
            this.refillRatePerNano = qps / TimeUnit.SECONDS.toNanos(1);
            this.tokens = this.capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        private synchronized boolean tryAcquire() {
            refill();
            if (tokens >= 1D) {
                tokens -= 1D;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos;
            if (elapsed <= 0L) {
                return;
            }
            tokens = Math.min(capacity, tokens + elapsed * refillRatePerNano);
            lastRefillNanos = now;
        }
    }
}
