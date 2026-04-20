package io.homeey.gateway.governance.api;

public record CircuitBreakerPolicy(
        boolean enabled,
        FailureMode failureMode,
        int failureRateThreshold,
        int minimumCalls,
        long openDurationMillis,
        int halfOpenMaxCalls
) implements GovernancePolicy {
    public static final String ABILITY = "circuitbreaker";

    @Override
    public String ability() {
        return ABILITY;
    }

    public static CircuitBreakerPolicy defaultPolicy() {
        return new CircuitBreakerPolicy(
                false,
                FailureMode.FAIL_CLOSE,
                50,
                20,
                10000L,
                5
        );
    }

    public static CircuitBreakerPolicy disabled() {
        return defaultPolicy();
    }

    public CircuitBreakerPolicy {
        failureMode = failureMode == null ? FailureMode.FAIL_CLOSE : failureMode;
        failureRateThreshold = Math.max(0, Math.min(100, failureRateThreshold));
        minimumCalls = Math.max(1, minimumCalls);
        openDurationMillis = Math.max(1L, openDurationMillis);
        halfOpenMaxCalls = Math.max(1, halfOpenMaxCalls);
    }
}
