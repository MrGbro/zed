package io.homeey.gateway.governance.api;

public record CircuitBreakerPolicy(
        boolean enabled,
        int failureRateThreshold,
        int minimumCalls,
        long openDurationMillis,
        int halfOpenMaxCalls
) {
    public static CircuitBreakerPolicy disabled() {
        return new CircuitBreakerPolicy(false, 50, 20, 10000L, 5);
    }
}
