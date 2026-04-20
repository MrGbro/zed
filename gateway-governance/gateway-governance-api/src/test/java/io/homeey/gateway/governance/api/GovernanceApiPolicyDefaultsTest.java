package io.homeey.gateway.governance.api;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernanceApiPolicyDefaultsTest {

    @Test
    void shouldDefaultRetryStatusesAndFailureMode() {
        RetryPolicy policy = RetryPolicy.disabled();
        assertEquals(FailureMode.FAIL_OPEN, policy.failureMode());
        assertTrue(policy.retryOnStatuses().contains(503));
    }

    @Test
    void shouldDefaultCircuitFailureModeToFailClose() {
        CircuitBreakerPolicy policy = CircuitBreakerPolicy.defaultPolicy();
        assertEquals(FailureMode.FAIL_CLOSE, policy.failureMode());
    }

    @Test
    void shouldNormalizeDegradePolicy() {
        DegradePolicy policy = new DegradePolicy(true, null, 50, "", null, Set.of("timeout"));
        assertEquals(FailureMode.FAIL_OPEN, policy.failureMode());
        assertEquals(100, policy.status());
        assertEquals("text/plain; charset=UTF-8", policy.contentType());
        assertEquals("", policy.body());
    }
}
