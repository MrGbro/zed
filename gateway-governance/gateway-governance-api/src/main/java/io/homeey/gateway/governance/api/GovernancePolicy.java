package io.homeey.gateway.governance.api;

/**
 * Aggregated governance policy for one route request execution.
 */
public record GovernancePolicy(
        boolean enabled,
        RateLimitPolicy rateLimitPolicy,
        CircuitBreakerPolicy circuitBreakerPolicy,
        TimeoutPolicy timeoutPolicy,
        RetryPolicy retryPolicy,
        DegradePolicy degradePolicy,
        String engineType
) {
    public static GovernancePolicy disabled() {
        return new GovernancePolicy(
                false,
                RateLimitPolicy.disabled(),
                CircuitBreakerPolicy.disabled(),
                TimeoutPolicy.disabled(),
                RetryPolicy.disabled(),
                DegradePolicy.disabled(),
                ""
        );
    }

    public GovernancePolicy {
        rateLimitPolicy = rateLimitPolicy == null ? RateLimitPolicy.disabled() : rateLimitPolicy;
        circuitBreakerPolicy = circuitBreakerPolicy == null ? CircuitBreakerPolicy.disabled() : circuitBreakerPolicy;
        timeoutPolicy = timeoutPolicy == null ? TimeoutPolicy.disabled() : timeoutPolicy;
        retryPolicy = retryPolicy == null ? RetryPolicy.disabled() : retryPolicy;
        degradePolicy = degradePolicy == null ? DegradePolicy.disabled() : degradePolicy;
        engineType = engineType == null ? "" : engineType;
    }
}
