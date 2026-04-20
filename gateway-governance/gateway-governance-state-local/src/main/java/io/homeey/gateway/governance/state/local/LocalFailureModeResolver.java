package io.homeey.gateway.governance.state.local;

import io.homeey.gateway.governance.api.CircuitBreakerPolicy;
import io.homeey.gateway.governance.api.DegradePolicy;
import io.homeey.gateway.governance.api.FailureMode;
import io.homeey.gateway.governance.api.FailureModeResolver;
import io.homeey.gateway.governance.api.RateLimitPolicy;
import io.homeey.gateway.governance.api.RetryPolicy;
import io.homeey.gateway.governance.api.TimeoutPolicy;

public final class LocalFailureModeResolver implements FailureModeResolver {
    @Override
    public FailureMode resolve(String ability, FailureMode configured) {
        if (configured != null) {
            return configured;
        }
        if (RateLimitPolicy.ABILITY.equals(ability) || CircuitBreakerPolicy.ABILITY.equals(ability)) {
            return FailureMode.FAIL_CLOSE;
        }
        if (RetryPolicy.ABILITY.equals(ability) || TimeoutPolicy.ABILITY.equals(ability) || DegradePolicy.ABILITY.equals(ability)) {
            return FailureMode.FAIL_OPEN;
        }
        return FailureMode.FAIL_OPEN;
    }
}
