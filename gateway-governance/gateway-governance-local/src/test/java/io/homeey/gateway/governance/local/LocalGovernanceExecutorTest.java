package io.homeey.gateway.governance.local;

import io.homeey.gateway.governance.api.CircuitBreakerPolicy;
import io.homeey.gateway.governance.api.DegradePolicy;
import io.homeey.gateway.governance.api.GovernancePolicy;
import io.homeey.gateway.governance.api.RateLimitPolicy;
import io.homeey.gateway.governance.api.RetryPolicy;
import io.homeey.gateway.governance.api.TimeoutPolicy;
import io.homeey.gateway.plugin.api.GatewayContext;
import io.homeey.gateway.proxy.api.ProxyResponse;
import io.homeey.gateway.transport.api.HttpRequestMessage;
import io.homeey.gateway.transport.api.HttpResponseMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalGovernanceExecutorTest {
    private final LocalGovernanceExecutor executor = new LocalGovernanceExecutor();

    @Test
    void shouldRejectWhenRateLimitExceeded() {
        GovernancePolicy policy = new GovernancePolicy(
                true,
                new RateLimitPolicy(true, 0.0001D, 1, "route"),
                CircuitBreakerPolicy.disabled(),
                TimeoutPolicy.disabled(),
                RetryPolicy.disabled(),
                DegradePolicy.disabled(),
                "local"
        );
        GatewayContext context = baseContext("r-rate");
        HttpResponseMessage first = execute(context, policy, completed(200));
        HttpResponseMessage second = execute(context, policy, completed(200));
        assertEquals(200, first.statusCode());
        assertEquals(429, second.statusCode());
    }

    @Test
    void shouldRetryAndEventuallySuccess() {
        GovernancePolicy policy = new GovernancePolicy(
                true,
                RateLimitPolicy.disabled(),
                CircuitBreakerPolicy.disabled(),
                TimeoutPolicy.disabled(),
                new RetryPolicy(true, 3, 0L, Set.of(503)),
                DegradePolicy.disabled(),
                "local"
        );
        GatewayContext context = baseContext("r-retry");
        AtomicInteger attempts = new AtomicInteger(0);
        HttpResponseMessage response = executor.execute(context, policy, () -> {
            int n = attempts.incrementAndGet();
            if (n < 3) {
                return completed(503);
            }
            return completed(200);
        }).toCompletableFuture().join();
        assertEquals(3, attempts.get());
        assertEquals(200, response.statusCode());
        assertEquals(3, context.attributes().get("governance.retry.attempts"));
    }

    @Test
    void shouldDegradeOnTimeout() {
        GovernancePolicy policy = new GovernancePolicy(
                true,
                RateLimitPolicy.disabled(),
                CircuitBreakerPolicy.disabled(),
                new TimeoutPolicy(true, 20L),
                new RetryPolicy(true, 1, 0L, Set.of(503)),
                new DegradePolicy(true, 503, "text/plain; charset=UTF-8", "fallback"),
                "local"
        );
        GatewayContext context = baseContext("r-timeout");
        HttpResponseMessage response = executor.execute(context, policy, () -> {
            CompletableFuture<ProxyResponse> future = new CompletableFuture<>();
            return future;
        }).toCompletableFuture().join();
        assertEquals(503, response.statusCode());
        assertTrue(new String(response.body(), StandardCharsets.UTF_8).contains("fallback"));
    }

    @Test
    void shouldOpenCircuitAfterFailures() {
        GovernancePolicy policy = new GovernancePolicy(
                true,
                RateLimitPolicy.disabled(),
                new CircuitBreakerPolicy(true, 50, 2, 5000L, 1),
                TimeoutPolicy.disabled(),
                RetryPolicy.disabled(),
                DegradePolicy.disabled(),
                "local"
        );
        GatewayContext firstCtx = baseContext("r-circuit");
        GatewayContext secondCtx = baseContext("r-circuit");
        GatewayContext thirdCtx = baseContext("r-circuit");
        execute(firstCtx, policy, failed(new RuntimeException("e1")));
        execute(secondCtx, policy, failed(new RuntimeException("e2")));
        HttpResponseMessage third = execute(thirdCtx, policy, completed(200));
        assertEquals(503, third.statusCode());
    }

    private GatewayContext baseContext(String routeId) {
        return new GatewayContext()
                .routeId(routeId)
                .request(new HttpRequestMessage("GET", "api.example.com", "/x", "", Map.of(), new byte[0]));
    }

    private CompletionStage<ProxyResponse> completed(int status) {
        return CompletableFuture.completedFuture(new ProxyResponse(status, Map.of(), "ok".getBytes(StandardCharsets.UTF_8)));
    }

    private CompletionStage<ProxyResponse> failed(Throwable throwable) {
        return CompletableFuture.failedFuture(throwable);
    }

    private HttpResponseMessage execute(
            GatewayContext context,
            GovernancePolicy policy,
            CompletionStage<ProxyResponse> result
    ) {
        return executor.execute(context, policy, () -> result).toCompletableFuture().join();
    }
}
