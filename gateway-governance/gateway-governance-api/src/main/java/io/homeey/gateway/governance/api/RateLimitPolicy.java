package io.homeey.gateway.governance.api;

public record RateLimitPolicy(
        boolean enabled,
        double qps,
        int burst,
        String key
) {
    public static RateLimitPolicy disabled() {
        return new RateLimitPolicy(false, 0D, 0, "route");
    }
}
