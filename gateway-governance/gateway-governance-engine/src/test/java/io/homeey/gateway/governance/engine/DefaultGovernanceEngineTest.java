package io.homeey.gateway.governance.engine;

import io.homeey.gateway.governance.api.CircuitBreakerPolicy;
import io.homeey.gateway.governance.api.CircuitBreakerPolicyHandler;
import io.homeey.gateway.governance.api.DegradePolicy;
import io.homeey.gateway.governance.api.DegradePolicyHandler;
import io.homeey.gateway.governance.api.FailureMode;
import io.homeey.gateway.governance.api.GovernanceExecutionContext;
import io.homeey.gateway.governance.api.GovernanceScheduler;
import io.homeey.gateway.governance.api.GovernanceStateStore;
import io.homeey.gateway.governance.api.PolicyFactory;
import io.homeey.gateway.governance.api.RateLimitPolicy;
import io.homeey.gateway.governance.api.RateLimitPolicyHandler;
import io.homeey.gateway.governance.api.RetryPolicy;
import io.homeey.gateway.governance.api.RetryPolicyHandler;
import io.homeey.gateway.governance.api.TimeoutPolicy;
import io.homeey.gateway.governance.api.TimeoutPolicyHandler;
import io.homeey.gateway.plugin.api.GatewayContext;
import io.homeey.gateway.transport.api.HttpResponseMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultGovernanceEngineTest {

    @Test
    void shouldRunPipelineAndReturnUpstreamResponse() {
        List<String> trace = new ArrayList<>();
        DefaultGovernanceEngine engine = new DefaultGovernanceEngine(
                new InMemoryStateStore(),
                new DirectScheduler(),
                (ability, configured) -> configured == null ? FailureMode.FAIL_OPEN : configured,
                new RateLimitFactory(trace),
                new CircuitFactory(trace),
                new RetryFactory(trace),
                new TimeoutFactory(trace),
                new DegradeFactory(trace),
                (context, policy, stateStore) -> {
                    trace.add("ratelimit.pre");
                    return true;
                },
                new CircuitBreakerPolicyHandler() {
                    @Override
                    public boolean allow(GovernanceExecutionContext context, CircuitBreakerPolicy policy, GovernanceStateStore stateStore) {
                        trace.add("circuit.pre");
                        return true;
                    }

                    @Override
                    public void record(GovernanceExecutionContext context, CircuitBreakerPolicy policy, GovernanceStateStore stateStore, HttpResponseMessage response, Throwable throwable) {
                        trace.add("circuit.record");
                    }
                },
                (context, policy, attempt, scheduler) -> {
                    trace.add("retry.around");
                    return attempt.get();
                },
                (context, policy, attempt, scheduler) -> {
                    trace.add("timeout.around");
                    return attempt.get();
                },
                (context, policy, kind, cause) -> new HttpResponseMessage(503, Map.of(), "degraded".getBytes(StandardCharsets.UTF_8))
        );

        GatewayContext gatewayContext = new GatewayContext().routeId("r1");
        GovernanceExecutionContext executionContext = new GovernanceExecutionContext(gatewayContext, Map.of("governance.enabled", true));
        HttpResponseMessage response = engine.execute(executionContext, () -> CompletableFuture.completedFuture(
                new HttpResponseMessage(200, Map.of(), "ok".getBytes(StandardCharsets.UTF_8))
        )).toCompletableFuture().join();

        assertEquals(200, response.statusCode());
        assertEquals(
                List.of(
                        "factory.ratelimit",
                        "factory.circuit",
                        "factory.retry",
                        "factory.timeout",
                        "factory.degrade",
                        "ratelimit.pre",
                        "circuit.pre",
                        "retry.around",
                        "timeout.around",
                        "circuit.record"
                ),
                trace
        );
    }

    private static final class InMemoryStateStore implements GovernanceStateStore {
        private final java.util.concurrent.ConcurrentHashMap<String, Object> values = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> T computeIfAbsent(String key, java.util.function.Supplier<T> supplier, Class<T> type) {
            return (T) values.computeIfAbsent(key, ignored -> supplier.get());
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

    private static final class RateLimitFactory implements PolicyFactory<RateLimitPolicy> {
        private final List<String> trace;

        private RateLimitFactory(List<String> trace) {
            this.trace = trace;
        }

        @Override
        public String ability() {
            return RateLimitPolicy.ABILITY;
        }

        @Override
        public RateLimitPolicy create(Map<String, Object> entries) {
            trace.add("factory.ratelimit");
            return new RateLimitPolicy(true, FailureMode.FAIL_CLOSE, 10D, 1, "route", "");
        }
    }

    private static final class CircuitFactory implements PolicyFactory<CircuitBreakerPolicy> {
        private final List<String> trace;

        private CircuitFactory(List<String> trace) {
            this.trace = trace;
        }

        @Override
        public String ability() {
            return CircuitBreakerPolicy.ABILITY;
        }

        @Override
        public CircuitBreakerPolicy create(Map<String, Object> entries) {
            trace.add("factory.circuit");
            return new CircuitBreakerPolicy(true, FailureMode.FAIL_CLOSE, 50, 1, 1000L, 1);
        }
    }

    private static final class RetryFactory implements PolicyFactory<RetryPolicy> {
        private final List<String> trace;

        private RetryFactory(List<String> trace) {
            this.trace = trace;
        }

        @Override
        public String ability() {
            return RetryPolicy.ABILITY;
        }

        @Override
        public RetryPolicy create(Map<String, Object> entries) {
            trace.add("factory.retry");
            return new RetryPolicy(true, FailureMode.FAIL_OPEN, 1, 0L, Set.of(503), true);
        }
    }

    private static final class TimeoutFactory implements PolicyFactory<TimeoutPolicy> {
        private final List<String> trace;

        private TimeoutFactory(List<String> trace) {
            this.trace = trace;
        }

        @Override
        public String ability() {
            return TimeoutPolicy.ABILITY;
        }

        @Override
        public TimeoutPolicy create(Map<String, Object> entries) {
            trace.add("factory.timeout");
            return new TimeoutPolicy(true, FailureMode.FAIL_OPEN, 1000L);
        }
    }

    private static final class DegradeFactory implements PolicyFactory<DegradePolicy> {
        private final List<String> trace;

        private DegradeFactory(List<String> trace) {
            this.trace = trace;
        }

        @Override
        public String ability() {
            return DegradePolicy.ABILITY;
        }

        @Override
        public DegradePolicy create(Map<String, Object> entries) {
            trace.add("factory.degrade");
            return DegradePolicy.defaultPolicy();
        }
    }
}
