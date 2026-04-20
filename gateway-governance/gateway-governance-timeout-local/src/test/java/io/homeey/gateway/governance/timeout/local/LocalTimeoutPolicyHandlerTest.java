package io.homeey.gateway.governance.timeout.local;

import io.homeey.gateway.governance.api.FailureMode;
import io.homeey.gateway.governance.api.GovernanceException;
import io.homeey.gateway.governance.api.GovernanceExecutionContext;
import io.homeey.gateway.governance.api.GovernanceFailureKind;
import io.homeey.gateway.governance.api.GovernanceScheduler;
import io.homeey.gateway.governance.api.TimeoutPolicy;
import io.homeey.gateway.plugin.api.GatewayContext;
import io.homeey.gateway.transport.api.HttpResponseMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalTimeoutPolicyHandlerTest {

    @Test
    void shouldApplyTimeout() {
        LocalTimeoutPolicyHandler handler = new LocalTimeoutPolicyHandler();
        TimeoutPolicy policy = new TimeoutPolicy(true, FailureMode.FAIL_OPEN, 10L);
        GovernanceExecutionContext context = new GovernanceExecutionContext(new GatewayContext().routeId("r1"), Map.of());

        CompletableFuture<HttpResponseMessage> future = handler.execute(context, policy,
                () -> new CompletableFuture<>(),
                new ImmediateTimeoutScheduler()
        ).toCompletableFuture();

        Throwable throwable = assertThrows(Throwable.class, future::join);
        Throwable cause = throwable.getCause();
        if (cause instanceof GovernanceException ge) {
            assertEquals(GovernanceFailureKind.TIMEOUT, ge.kind());
        }
    }

    private static final class ImmediateTimeoutScheduler implements GovernanceScheduler {
        @Override
        public <T> CompletionStage<T> withTimeout(CompletionStage<T> origin, long timeoutMillis, String message) {
            return CompletableFuture.failedFuture(new GovernanceException(GovernanceFailureKind.TIMEOUT, "timeout"));
        }

        @Override
        public CompletionStage<Void> delay(long delayMillis) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
