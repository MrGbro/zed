package io.homeey.gateway.governance.api;

public record TimeoutPolicy(
        boolean enabled,
        long durationMillis
) {
    public static TimeoutPolicy disabled() {
        return new TimeoutPolicy(false, 0L);
    }
}
