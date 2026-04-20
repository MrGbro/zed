package io.homeey.gateway.governance.retry.local;

import io.homeey.gateway.governance.api.GovernanceException;
import io.homeey.gateway.governance.api.GovernanceExecutionContext;
import io.homeey.gateway.governance.api.GovernanceFailureKind;
import io.homeey.gateway.governance.api.GovernanceScheduler;
import io.homeey.gateway.governance.api.RetryPolicy;
import io.homeey.gateway.plugin.api.GatewayContext;
import io.homeey.gateway.transport.api.HttpResponseMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalRetryPolicyHandlerTest {

    @Test
    void shouldRetryUntilSuccess() {
        LocalRetryPolicyHandler handler = new LocalRetryPolicyHandler();
        RetryPolicy policy = new RetryPolicy(true, io.homeey.gateway.governance.api.FailureMode.FAIL_OPEN, 3, 0L, Set.of(503), true);
        GovernanceExecutionContext context = new GovernanceExecutionContext(new GatewayContext().routeId("r1"), Map.of());
        AtomicInteger attempts = new AtomicInteger();

        HttpResponseMessage response = handler.execute(context, policy, () -> {
            int n = attempts.incrementAndGet();
            if (n < 3) {
                return CompletableFuture.completedFuture(new HttpResponseMessage(503, Map.of(), new byte[0]));
            }
            return CompletableFuture.completedFuture(new HttpResponseMessage(200, Map.of(), new byte[0]));
        }, new DirectScheduler()).toCompletableFuture().join();

        assertEquals(3, attempts.get());
        assertEquals(200, response.statusCode());
    }

    @Test
    void shouldFailWhenExhausted() {
        LocalRetryPolicyHandler handler = new LocalRetryPolicyHandler();
        RetryPolicy policy = new RetryPolicy(true, io.homeey.gateway.governance.api.FailureMode.FAIL_OPEN, 2, 0L, Set.of(503), true);
        GovernanceExecutionContext context = new GovernanceExecutionContext(new GatewayContext().routeId("r1"), Map.of());

        CompletableFuture<HttpResponseMessage> future = handler.execute(context, policy,
                () -> CompletableFuture.completedFuture(new HttpResponseMessage(503, Map.of(), new byte[0])),
                new DirectScheduler()
        ).toCompletableFuture();

        Throwable throwable = assertThrows(Throwable.class, future::join);
        Throwable cause = throwable.getCause();
        if (cause instanceof GovernanceException ge) {
            assertEquals(GovernanceFailureKind.RETRY_EXHAUSTED, ge.kind());
        }
    }

    private static final class DirectScheduler implements GovernanceScheduler {
        @Override
        public <T> CompletionStage<T> withTimeout(CompletionStage<T> origin, long timeoutMillis, String message) {
            return origin;
        }

        @Override
        public CompletionStage<Void> delay(long delayMillis) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
