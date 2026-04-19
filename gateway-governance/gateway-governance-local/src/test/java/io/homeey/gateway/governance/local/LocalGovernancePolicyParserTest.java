package io.homeey.gateway.governance.local;

import io.homeey.gateway.governance.api.GovernancePolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalGovernancePolicyParserTest {

    private final LocalGovernancePolicyParser parser = new LocalGovernancePolicyParser();

    @Test
    void shouldDisableWhenGovernanceDisabled() {
        GovernancePolicy policy = parser.parse(Map.of());
        assertFalse(policy.enabled());
    }

    @Test
    void shouldEnableOnlyConfiguredCapabilities() {
        GovernancePolicy policy = parser.parse(Map.of(
                "governance.enabled", true,
                "governance.ratelimit.enabled", true,
                "governance.ratelimit.qps", 10
        ));
        assertTrue(policy.enabled());
        assertTrue(policy.rateLimitPolicy().enabled());
        assertFalse(policy.circuitBreakerPolicy().enabled());
        assertFalse(policy.timeoutPolicy().enabled());
        assertFalse(policy.retryPolicy().enabled());
        assertFalse(policy.degradePolicy().enabled());
    }

    @Test
    void shouldFallbackToDisabledOnInvalidValues() {
        GovernancePolicy policy = parser.parse(Map.of(
                "governance.enabled", true,
                "governance.ratelimit.enabled", true,
                "governance.ratelimit.qps", "bad-value"
        ));
        assertTrue(policy.enabled());
        assertFalse(policy.rateLimitPolicy().enabled());
    }
}
