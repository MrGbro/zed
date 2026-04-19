package io.homeey.gateway.governance.api;

import java.util.Set;

public record RetryPolicy(
        boolean enabled,
        int maxAttempts,
        long backoffMillis,
        Set<Integer> retryOnStatuses
) {
    public static RetryPolicy disabled() {
        return new RetryPolicy(false, 1, 0L, Set.of(502, 503, 504));
    }

    public RetryPolicy {
        retryOnStatuses = retryOnStatuses == null ? Set.of(502, 503, 504) : Set.copyOf(retryOnStatuses);
    }
}
