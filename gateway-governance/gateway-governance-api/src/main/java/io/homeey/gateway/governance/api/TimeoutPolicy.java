package io.homeey.gateway.governance.api;

public record TimeoutPolicy(
        boolean enabled,
        FailureMode failureMode,
        long durationMillis
) implements GovernancePolicy {
    public static final String ABILITY = "timeout";

    @Override
    public String ability() {
        return ABILITY;
    }

    public static TimeoutPolicy defaultPolicy() {
        return new TimeoutPolicy(false, FailureMode.FAIL_OPEN, 0L);
    }

    public static TimeoutPolicy disabled() {
        return defaultPolicy();
    }

    public TimeoutPolicy {
        failureMode = failureMode == null ? FailureMode.FAIL_OPEN : failureMode;
        durationMillis = Math.max(0L, durationMillis);
    }
}
