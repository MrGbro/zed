package io.homeey.gateway.governance.local;

import io.homeey.gateway.governance.api.CircuitBreakerPolicy;
import io.homeey.gateway.governance.api.DegradePolicy;
import io.homeey.gateway.governance.api.GovernanceExecutor;
import io.homeey.gateway.governance.api.GovernancePolicy;
import io.homeey.gateway.governance.api.RateLimitPolicy;
import io.homeey.gateway.governance.api.RetryPolicy;
import io.homeey.gateway.governance.api.TimeoutPolicy;
import io.homeey.gateway.plugin.api.GatewayContext;
import io.homeey.gateway.proxy.api.ProxyResponse;
import io.homeey.gateway.transport.api.HttpResponseMessage;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class LocalGovernanceExecutor implements GovernanceExecutor {
    private final ConcurrentHashMap<String, TokenBucket> tokenBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CircuitState> circuitStates = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<HttpResponseMessage> execute(
            GatewayContext context,
            GovernancePolicy policy,
            Supplier<CompletionStage<ProxyResponse>> upstreamCall
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(upstreamCall, "upstreamCall");

        if (policy == null || !policy.enabled()) {
            return invokeUpstream(upstreamCall);
        }

        DegradePolicy degrade = policy.degradePolicy();

        if (!allowRateLimit(context, policy.rateLimitPolicy())) {
            return CompletableFuture.completedFuture(buildRejectedResponse("rate_limited", 429, "too many requests", degrade));
        }
        if (!allowCircuit(context, policy.circuitBreakerPolicy())) {
            return CompletableFuture.completedFuture(buildRejectedResponse("circuit_open", 503, "circuit open", degrade));
        }

        return executeWithRetryAndTimeout(context, policy, upstreamCall);
    }

    private CompletionStage<HttpResponseMessage> executeWithRetryAndTimeout(
            GatewayContext context,
            GovernancePolicy policy,
            Supplier<CompletionStage<ProxyResponse>> upstreamCall
    ) {
        RetryPolicy retry = policy.retryPolicy();
        int attempts = retry.enabled() ? retry.maxAttempts() : 1;
        long backoff = retry.enabled() ? retry.backoffMillis() : 0L;
        Set<Integer> retryStatuses = retry.retryOnStatuses();

        CompletableFuture<HttpResponseMessage> result = new CompletableFuture<>();
        runAttempt(context, policy, upstreamCall, 1, attempts, backoff, retryStatuses, result);
        return result;
    }

    private void runAttempt(
            GatewayContext context,
            GovernancePolicy policy,
            Supplier<CompletionStage<ProxyResponse>> upstreamCall,
            int attempt,
            int maxAttempts,
            long backoffMillis,
            Set<Integer> retryStatuses,
            CompletableFuture<HttpResponseMessage> result
    ) {
        context.attributes().put("governance.retry.attempts", attempt);
        callWithTimeout(upstreamCall, policy.timeoutPolicy())
                .whenComplete((response, throwable) -> {
                    if (result.isDone()) {
                        return;
                    }
                    boolean success = throwable == null && response != null && response.statusCode() < 500;
                    recordCircuit(context.routeId(), policy.circuitBreakerPolicy(), success);

                    if (throwable == null && response != null) {
                        HttpResponseMessage out = toHttpResponse(response);
                        if (attempt < maxAttempts && retryStatuses.contains(response.statusCode())) {
                            sleep(backoffMillis);
                            runAttempt(context, policy, upstreamCall, attempt + 1, maxAttempts, backoffMillis, retryStatuses, result);
                            return;
                        }
                        result.complete(out);
                        return;
                    }

                    if (attempt < maxAttempts) {
                        context.attributes().put("governance.retry", true);
                        sleep(backoffMillis);
                        runAttempt(context, policy, upstreamCall, attempt + 1, maxAttempts, backoffMillis, retryStatuses, result);
                        return;
                    }

                    if (throwable instanceof TimeoutException) {
                        context.attributes().put("governance.timeout", true);
                    }
                    DegradePolicy degrade = policy.degradePolicy();
                    HttpResponseMessage rejected = buildRejectedResponse(
                            "governance_failure",
                            throwable instanceof TimeoutException ? 504 : 502,
                            throwable == null ? "upstream error" : throwable.getMessage(),
                            degrade
                    );
                    result.complete(rejected);
                });
    }

    private CompletionStage<ProxyResponse> callWithTimeout(
            Supplier<CompletionStage<ProxyResponse>> upstreamCall,
            TimeoutPolicy timeoutPolicy
    ) {
        CompletionStage<ProxyResponse> origin = upstreamCall.get();
        if (timeoutPolicy == null || !timeoutPolicy.enabled()) {
            return origin;
        }
        CompletableFuture<ProxyResponse> timeoutFuture = new CompletableFuture<>();
        Thread t = new Thread(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(timeoutPolicy.durationMillis());
                timeoutFuture.completeExceptionally(new TimeoutException("request timeout"));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        t.setDaemon(true);
        t.start();

        CompletableFuture<ProxyResponse> originFuture = origin.toCompletableFuture();
        return originFuture.applyToEither(timeoutFuture, v -> v);
    }

    private CompletionStage<HttpResponseMessage> invokeUpstream(Supplier<CompletionStage<ProxyResponse>> upstreamCall) {
        return upstreamCall.get().thenApply(this::toHttpResponse);
    }

    private HttpResponseMessage toHttpResponse(ProxyResponse response) {
        return new HttpResponseMessage(
                response.statusCode(),
                response.headers() == null ? Map.of() : response.headers(),
                response.body() == null ? new byte[0] : response.body()
        );
    }

    private boolean allowRateLimit(GatewayContext context, RateLimitPolicy policy) {
        if (policy == null || !policy.enabled()) {
            return true;
        }
        String key = resolveRateLimitKey(context, policy);
        TokenBucket bucket = tokenBuckets.computeIfAbsent(key, ignored -> new TokenBucket(policy.burst(), policy.qps()));
        return bucket.tryAcquire();
    }

    private String resolveRateLimitKey(GatewayContext context, RateLimitPolicy policy) {
        if ("ip".equalsIgnoreCase(policy.key())) {
            String forwarded = header(context, "x-forwarded-for");
            if (forwarded != null && !forwarded.isBlank()) {
                return "ip:" + forwarded;
            }
        }
        return "route:" + (context.routeId() == null ? "" : context.routeId());
    }

    private boolean allowCircuit(GatewayContext context, CircuitBreakerPolicy policy) {
        if (policy == null || !policy.enabled()) {
            return true;
        }
        String key = "route:" + (context.routeId() == null ? "" : context.routeId());
        CircuitState state = circuitStates.computeIfAbsent(key, ignored -> new CircuitState());
        return state.allowRequest(policy);
    }

    private void recordCircuit(String routeId, CircuitBreakerPolicy policy, boolean success) {
        if (policy == null || !policy.enabled()) {
            return;
        }
        String key = "route:" + (routeId == null ? "" : routeId);
        CircuitState state = circuitStates.computeIfAbsent(key, ignored -> new CircuitState());
        state.recordResult(policy, success);
    }

    private HttpResponseMessage buildRejectedResponse(
            String reason,
            int defaultStatus,
            String defaultMessage,
            DegradePolicy degrade
    ) {
        if (degrade != null && degrade.enabled()) {
            return new HttpResponseMessage(
                    degrade.status(),
                    Map.of("content-type", degrade.contentType()),
                    degrade.body().getBytes(StandardCharsets.UTF_8)
            );
        }
        return new HttpResponseMessage(
                defaultStatus,
                Map.of("content-type", "text/plain; charset=UTF-8"),
                defaultMessage.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void sleep(long millis) {
        if (millis <= 0L) {
            return;
        }
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String header(GatewayContext context, String name) {
        if (context.request() == null || context.request().headers() == null || name == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : context.request().headers().entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static final class TokenBucket {
        private final int capacity;
        private final double refillRatePerNano;
        private double tokens;
        private long lastRefillNanos;

        private TokenBucket(int capacity, double qps) {
            this.capacity = Math.max(1, capacity);
            this.refillRatePerNano = qps / TimeUnit.SECONDS.toNanos(1);
            this.tokens = this.capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        private synchronized boolean tryAcquire() {
            refill();
            if (tokens >= 1D) {
                tokens -= 1D;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos;
            if (elapsed <= 0L) {
                return;
            }
            double toAdd = elapsed * refillRatePerNano;
            tokens = Math.min(capacity, tokens + toAdd);
            lastRefillNanos = now;
        }
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
