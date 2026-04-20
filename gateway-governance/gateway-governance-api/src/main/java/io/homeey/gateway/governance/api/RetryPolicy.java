package io.homeey.gateway.governance.api;

import java.util.Set;

public record RetryPolicy(
        boolean enabled,
        FailureMode failureMode,
        int maxAttempts,
        long backoffMillis,
        Set<Integer> retryOnStatuses,
        boolean retryOnTimeout
) implements GovernancePolicy {
    public static final String ABILITY = "retry";

    @Override
    public String ability() {
        return ABILITY;
    }

    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(
                false,
                FailureMode.FAIL_OPEN,
                1,
                0L,
                Set.of(502, 503, 504),
                true
        );
    }

    public static RetryPolicy disabled() {
        return defaultPolicy();
    }

    public RetryPolicy {
        failureMode = failureMode == null ? FailureMode.FAIL_OPEN : failureMode;
        maxAttempts = Math.max(1, maxAttempts);
        backoffMillis = Math.max(0L, backoffMillis);
        retryOnStatuses = retryOnStatuses == null ? Set.of(502, 503, 504) : Set.copyOf(retryOnStatuses);
    }
}
