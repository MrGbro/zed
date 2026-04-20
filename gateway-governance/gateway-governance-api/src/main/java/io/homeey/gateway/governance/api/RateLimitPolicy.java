package io.homeey.gateway.governance.api;

public record RateLimitPolicy(
        boolean enabled,
        FailureMode failureMode,
        double qps,
        int burst,
        String provider,
        String keyType,
        String keyHeader
) implements GovernancePolicy {
    public static final String ABILITY = "ratelimit";

    @Override
    public String ability() {
        return ABILITY;
    }

    public static RateLimitPolicy defaultPolicy() {
        return new RateLimitPolicy(
                false,
                FailureMode.FAIL_CLOSE,
                0D,
                1,
                "local",
                "route",
                ""
        );
    }

    public static RateLimitPolicy disabled() {
        return defaultPolicy();
    }

    public RateLimitPolicy {
        failureMode = failureMode == null ? FailureMode.FAIL_CLOSE : failureMode;
        burst = Math.max(1, burst);
        provider = provider == null || provider.isBlank() ? "local" : provider.trim().toLowerCase();
        keyType = keyType == null || keyType.isBlank() ? "route" : keyType.trim().toLowerCase();
        if (!"route".equals(keyType) && !"ip".equals(keyType) && !"header".equals(keyType)) {
            keyType = "route";
        }
        keyHeader = keyHeader == null ? "" : keyHeader.trim();
        if (!enabled) {
            qps = 0D;
            keyHeader = "";
        }
    }
}
