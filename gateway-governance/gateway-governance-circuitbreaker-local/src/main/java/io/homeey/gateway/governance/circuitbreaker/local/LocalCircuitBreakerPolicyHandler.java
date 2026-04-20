package io.homeey.gateway.governance.circuitbreaker.local;

import io.homeey.gateway.governance.api.CircuitBreakerPolicy;
import io.homeey.gateway.governance.api.CircuitBreakerPolicyHandler;
import io.homeey.gateway.governance.api.GovernanceExecutionContext;
import io.homeey.gateway.governance.api.GovernanceStateStore;
import io.homeey.gateway.transport.api.HttpResponseMessage;

import java.util.concurrent.atomic.AtomicLong;

public final class LocalCircuitBreakerPolicyHandler implements CircuitBreakerPolicyHandler {
    @Override
    public boolean allow(GovernanceExecutionContext context, CircuitBreakerPolicy policy, GovernanceStateStore stateStore) {
        if (!policy.enabled()) {
            return true;
        }
        String key = "circuit:" + (context.routeId() == null ? "" : context.routeId());
        CircuitState state = stateStore.computeIfAbsent(key, CircuitState::new, CircuitState.class);
        return state.allowRequest(policy);
    }

    @Override
    public void record(
            GovernanceExecutionContext context,
            CircuitBreakerPolicy policy,
            GovernanceStateStore stateStore,
            HttpResponseMessage response,
            Throwable throwable
    ) {
        if (!policy.enabled()) {
            return;
        }
        String key = "circuit:" + (context.routeId() == null ? "" : context.routeId());
        CircuitState state = stateStore.computeIfAbsent(key, CircuitState::new, CircuitState.class);
        boolean success = throwable == null && response != null && response.statusCode() < 500;
        state.recordResult(policy, success);
    }

    private static final class CircuitState {
        private enum State {CLOSED, OPEN, HALF_OPEN}

        private final AtomicLong totalCalls = new AtomicLong();
        private final AtomicLong failedCalls = new AtomicLong();
        private volatile State state = State.CLOSED;
        private volatile long openedAtMillis = 0L;
        private volatile int halfOpenCalls = 0;
        private volatile int halfOpenSuccess = 0;

        private synchronized boolean allowRequest(CircuitBreakerPolicy policy) {
            if (state == State.CLOSED) {
                return true;
            }
            long now = System.currentTimeMillis();
            if (state == State.OPEN) {
                if (now - openedAtMillis >= policy.openDurationMillis()) {
                    state = State.HALF_OPEN;
                    halfOpenCalls = 0;
                    halfOpenSuccess = 0;
                } else {
                    return false;
                }
            }
            if (state == State.HALF_OPEN) {
                if (halfOpenCalls >= policy.halfOpenMaxCalls()) {
                    return false;
                }
                halfOpenCalls++;
            }
            return true;
        }

        private synchronized void recordResult(CircuitBreakerPolicy policy, boolean success) {
            if (state == State.HALF_OPEN) {
                if (success) {
                    halfOpenSuccess++;
                    if (halfOpenSuccess >= policy.halfOpenMaxCalls()) {
                        state = State.CLOSED;
                        totalCalls.set(0L);
                        failedCalls.set(0L);
                    }
                } else {
                    state = State.OPEN;
                    openedAtMillis = System.currentTimeMillis();
                }
                return;
            }

            totalCalls.incrementAndGet();
            if (!success) {
                failedCalls.incrementAndGet();
            }
            long total = totalCalls.get();
            if (total < policy.minimumCalls()) {
                return;
            }
            double failureRate = (failedCalls.get() * 100D) / total;
            if (failureRate >= policy.failureRateThreshold()) {
                state = State.OPEN;
                openedAtMillis = System.currentTimeMillis();
            }
        }
    }
}
