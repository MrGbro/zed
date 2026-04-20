package io.homeey.gateway.governance.ratelimit.local;

import io.homeey.gateway.governance.api.FailureMode;
import io.homeey.gateway.governance.api.RateLimitPolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalRateLimitPolicyFactoryTest {

    @Test
    void shouldParseProviderAndPolicyFields() {
        LocalRateLimitPolicyFactory factory = new LocalRateLimitPolicyFactory();
        RateLimitPolicy policy = factory.create(Map.of(
                "governance.ratelimit.enabled", true,
                "governance.ratelimit.failureMode", "fail-open",
                "governance.ratelimit.qps", 120.5,
                "governance.ratelimit.burst", 150,
                "governance.ratelimit.provider", "sentinel",
                "governance.ratelimit.keyType", "header",
                "governance.ratelimit.keyHeader", "X-UID"
        ));

        assertTrue(policy.enabled());
        assertEquals(FailureMode.FAIL_OPEN, policy.failureMode());
        assertEquals(120.5, policy.qps());
        assertEquals(150, policy.burst());
        assertEquals("sentinel", policy.provider());
        assertEquals("header", policy.keyType());
        assertEquals("X-UID", policy.keyHeader());
    }

    @Test
    void shouldFallbackToLocalProviderWhenMissing() {
        LocalRateLimitPolicyFactory factory = new LocalRateLimitPolicyFactory();
        RateLimitPolicy policy = factory.create(Map.of(
                "governance.ratelimit.enabled", true,
                "governance.ratelimit.qps", 10
        ));

        assertTrue(policy.enabled());
        assertEquals("local", policy.provider());
    }

    @Test
    void shouldReturnDisabledPolicyWhenQpsInvalid() {
        LocalRateLimitPolicyFactory factory = new LocalRateLimitPolicyFactory();
        RateLimitPolicy policy = factory.create(Map.of(
                "governance.ratelimit.enabled", true,
                "governance.ratelimit.qps", 0
        ));

        assertFalse(policy.enabled());
        assertEquals("local", policy.provider());
    }
}
