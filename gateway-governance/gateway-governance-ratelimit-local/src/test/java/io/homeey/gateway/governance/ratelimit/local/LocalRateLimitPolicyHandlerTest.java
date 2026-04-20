package io.homeey.gateway.governance.ratelimit.local;

import io.homeey.gateway.governance.api.FailureMode;
import io.homeey.gateway.governance.api.GovernanceExecutionContext;
import io.homeey.gateway.governance.api.GovernanceStateStore;
import io.homeey.gateway.governance.api.RateLimitPolicy;
import io.homeey.gateway.plugin.api.GatewayContext;
import io.homeey.gateway.transport.api.HttpRequestMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalRateLimitPolicyHandlerTest {

    @Test
    void shouldThrottleByRouteWithQpsOne() {
        LocalRateLimitPolicyHandler handler = new LocalRateLimitPolicyHandler();
        GovernanceStateStore stateStore = new InMemoryStore();
        RateLimitPolicy policy = new RateLimitPolicy(true, FailureMode.FAIL_CLOSE, 1D, 1, "local", "route", "");
        GovernanceExecutionContext context = context("route-a", Map.of());

        boolean first = handler.allow(context, policy, stateStore);
        boolean second = handler.allow(context, policy, stateStore);

        assertTrue(first);
        assertFalse(second);
    }

    @Test
    void shouldUseHeaderAsKeyWhenConfigured() {
        LocalRateLimitPolicyHandler handler = new LocalRateLimitPolicyHandler();
        GovernanceStateStore stateStore = new InMemoryStore();
        RateLimitPolicy policy = new RateLimitPolicy(true, FailureMode.FAIL_CLOSE, 1D, 1, "local", "header", "X-UID");

        GovernanceExecutionContext a1 = context("route-a", Map.of("X-UID", "1001"));
        GovernanceExecutionContext a2 = context("route-a", Map.of("x-uid", "1001"));
        GovernanceExecutionContext b1 = context("route-a", Map.of("X-UID", "2002"));

        assertTrue(handler.allow(a1, policy, stateStore));
        assertFalse(handler.allow(a2, policy, stateStore));
        assertTrue(handler.allow(b1, policy, stateStore));
    }

    private GovernanceExecutionContext context(String routeId, Map<String, String> headers) {
        GatewayContext gatewayContext = new GatewayContext()
                .routeId(routeId)
                .request(new HttpRequestMessage("GET", "localhost", "/test", "", headers, new byte[0]));
        return new GovernanceExecutionContext(gatewayContext, Map.of());
    }

    private static final class InMemoryStore implements GovernanceStateStore {
        private final ConcurrentHashMap<String, Object> values = new ConcurrentHashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> T computeIfAbsent(String key, Supplier<T> supplier, Class<T> type) {
            return (T) values.computeIfAbsent(key, ignored -> supplier.get());
        }
    }
}
