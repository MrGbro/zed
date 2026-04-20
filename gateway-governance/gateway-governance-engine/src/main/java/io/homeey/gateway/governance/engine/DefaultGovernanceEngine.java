package io.homeey.gateway.governance.engine;

import io.homeey.gateway.common.spi.ExtensionLoader;
import io.homeey.gateway.governance.api.CircuitBreakerPolicy;
import io.homeey.gateway.governance.api.CircuitBreakerPolicyHandler;
import io.homeey.gateway.governance.api.DegradePolicy;
import io.homeey.gateway.governance.api.DegradePolicyHandler;
import io.homeey.gateway.governance.api.FailureMode;
import io.homeey.gateway.governance.api.FailureModeResolver;
import io.homeey.gateway.governance.api.GovernanceEngine;
import io.homeey.gateway.governance.api.GovernanceException;
import io.homeey.gateway.governance.api.GovernanceExecutionContext;
import io.homeey.gateway.governance.api.GovernanceFailureKind;
import io.homeey.gateway.governance.api.GovernancePolicy;
import io.homeey.gateway.governance.api.GovernanceScheduler;
import io.homeey.gateway.governance.api.GovernanceStateStore;
import io.homeey.gateway.governance.api.PolicyFactory;
import io.homeey.gateway.governance.api.RateLimitPolicy;
import io.homeey.gateway.governance.api.RateLimitPolicyHandler;
import io.homeey.gateway.governance.api.RetryPolicy;
import io.homeey.gateway.governance.api.RetryPolicyHandler;
import io.homeey.gateway.governance.api.TimeoutPolicy;
import io.homeey.gateway.governance.api.TimeoutPolicyHandler;
import io.homeey.gateway.transport.api.HttpResponseMessage;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class DefaultGovernanceEngine implements GovernanceEngine {
    private final GovernanceStateStore stateStore;
    private final GovernanceScheduler scheduler;
    private final FailureModeResolver failureModeResolver;
    private final PolicyFactory<RateLimitPolicy> rateLimitPolicyFactory;
    private final PolicyFactory<CircuitBreakerPolicy> circuitBreakerPolicyFactory;
    private final PolicyFactory<RetryPolicy> retryPolicyFactory;
    private final PolicyFactory<TimeoutPolicy> timeoutPolicyFactory;
    private final PolicyFactory<DegradePolicy> degradePolicyFactory;
    private final RateLimitPolicyHandler rateLimitHandler;
    private final ExtensionLoader<RateLimitPolicyHandler> rateLimitHandlerLoader;
    private final CircuitBreakerPolicyHandler circuitBreakerHandler;
    private final RetryPolicyHandler retryHandler;
    private final TimeoutPolicyHandler timeoutHandler;
    private final DegradePolicyHandler degradeHandler;

    public DefaultGovernanceEngine() {
        ExtensionLoader<GovernanceStateStore> stateStoreLoader = ExtensionLoader.getExtensionLoader(GovernanceStateStore.class);
        ExtensionLoader<GovernanceScheduler> schedulerLoader = ExtensionLoader.getExtensionLoader(GovernanceScheduler.class);
        ExtensionLoader<FailureModeResolver> resolverLoader = ExtensionLoader.getExtensionLoader(FailureModeResolver.class);
        ExtensionLoader<PolicyFactory> factoryLoader = ExtensionLoader.getExtensionLoader(PolicyFactory.class);

        this.stateStore = stateStoreLoader.getDefaultExtension();
        this.scheduler = schedulerLoader.getDefaultExtension();
        this.failureModeResolver = resolverLoader.getDefaultExtension();

        this.rateLimitPolicyFactory = castFactory(factoryLoader.getExtension("ratelimit"));
        this.circuitBreakerPolicyFactory = castFactory(factoryLoader.getExtension("circuitbreaker"));
        this.retryPolicyFactory = castFactory(factoryLoader.getExtension("retry"));
        this.timeoutPolicyFactory = castFactory(factoryLoader.getExtension("timeout"));
        this.degradePolicyFactory = castFactory(factoryLoader.getExtension("degrade"));

        ExtensionLoader<RateLimitPolicyHandler> rateLimitHandlerLoader = ExtensionLoader.getExtensionLoader(RateLimitPolicyHandler.class);
        ExtensionLoader<CircuitBreakerPolicyHandler> circuitHandlerLoader = ExtensionLoader.getExtensionLoader(CircuitBreakerPolicyHandler.class);
        ExtensionLoader<RetryPolicyHandler> retryHandlerLoader = ExtensionLoader.getExtensionLoader(RetryPolicyHandler.class);
        ExtensionLoader<TimeoutPolicyHandler> timeoutHandlerLoader = ExtensionLoader.getExtensionLoader(TimeoutPolicyHandler.class);
        ExtensionLoader<DegradePolicyHandler> degradeHandlerLoader = ExtensionLoader.getExtensionLoader(DegradePolicyHandler.class);

        this.rateLimitHandler = rateLimitHandlerLoader.getDefaultExtension();
        this.rateLimitHandlerLoader = rateLimitHandlerLoader;
        this.circuitBreakerHandler = circuitHandlerLoader.getDefaultExtension();
        this.retryHandler = retryHandlerLoader.getDefaultExtension();
        this.timeoutHandler = timeoutHandlerLoader.getDefaultExtension();
        this.degradeHandler = degradeHandlerLoader.getDefaultExtension();
    }

    DefaultGovernanceEngine(
            GovernanceStateStore stateStore,
            GovernanceScheduler scheduler,
            FailureModeResolver failureModeResolver,
            PolicyFactory<RateLimitPolicy> rateLimitPolicyFactory,
            PolicyFactory<CircuitBreakerPolicy> circuitBreakerPolicyFactory,
            PolicyFactory<RetryPolicy> retryPolicyFactory,
            PolicyFactory<TimeoutPolicy> timeoutPolicyFactory,
            PolicyFactory<DegradePolicy> degradePolicyFactory,
            RateLimitPolicyHandler rateLimitHandler,
            CircuitBreakerPolicyHandler circuitBreakerHandler,
            RetryPolicyHandler retryHandler,
            TimeoutPolicyHandler timeoutHandler,
            DegradePolicyHandler degradeHandler
    ) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.failureModeResolver = Objects.requireNonNull(failureModeResolver, "failureModeResolver");
        this.rateLimitPolicyFactory = Objects.requireNonNull(rateLimitPolicyFactory, "rateLimitPolicyFactory");
        this.circuitBreakerPolicyFactory = Objects.requireNonNull(circuitBreakerPolicyFactory, "circuitBreakerPolicyFactory");
        this.retryPolicyFactory = Objects.requireNonNull(retryPolicyFactory, "retryPolicyFactory");
        this.timeoutPolicyFactory = Objects.requireNonNull(timeoutPolicyFactory, "timeoutPolicyFactory");
        this.degradePolicyFactory = Objects.requireNonNull(degradePolicyFactory, "degradePolicyFactory");
        this.rateLimitHandler = Objects.requireNonNull(rateLimitHandler, "rateLimitHandler");
        this.rateLimitHandlerLoader = null;
        this.circuitBreakerHandler = Objects.requireNonNull(circuitBreakerHandler, "circuitBreakerHandler");
        this.retryHandler = Objects.requireNonNull(retryHandler, "retryHandler");
        this.timeoutHandler = Objects.requireNonNull(timeoutHandler, "timeoutHandler");
        this.degradeHandler = Objects.requireNonNull(degradeHandler, "degradeHandler");
    }

    @Override
    public CompletionStage<HttpResponseMessage> execute(
            GovernanceExecutionContext context,
            Supplier<CompletionStage<HttpResponseMessage>> upstreamCall
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(upstreamCall, "upstreamCall");

        Map<String, Object> entries = context.policyEntries();
        if (!GovernanceEngine.enabled(entries)) {
            return upstreamCall.get();
        }

        RateLimitPolicy rateLimitPolicy = rateLimitPolicyFactory.create(entries);
        CircuitBreakerPolicy circuitBreakerPolicy = circuitBreakerPolicyFactory.create(entries);
        RetryPolicy retryPolicy = retryPolicyFactory.create(entries);
        TimeoutPolicy timeoutPolicy = timeoutPolicyFactory.create(entries);
        DegradePolicy degradePolicy = degradePolicyFactory.create(entries);

        HttpResponseMessage rateLimited = applyRateLimit(context, rateLimitPolicy, degradePolicy);
        if (rateLimited != null) {
            return CompletableFuture.completedFuture(rateLimited);
        }

        HttpResponseMessage circuitRejected = applyCircuitPreCheck(context, circuitBreakerPolicy, degradePolicy);
        if (circuitRejected != null) {
            return CompletableFuture.completedFuture(circuitRejected);
        }

        CompletionStage<HttpResponseMessage> stage = retryHandler.execute(
                context,
                retryPolicy,
                () -> timeoutHandler.execute(context, timeoutPolicy, upstreamCall, scheduler),
                scheduler
        );

        return stage.handle((response, throwable) -> {
            Throwable cause = unwrap(throwable);
            try {
                circuitBreakerHandler.record(context, circuitBreakerPolicy, stateStore, response, cause);
            } catch (Throwable recordError) {
                context.attributes().put("governance.circuitbreaker.bypassed", true);
            }

            if (cause == null) {
                return CompletableFuture.completedFuture(response);
            }

            GovernanceFailureKind kind = resolveFailureKind(cause);
            return CompletableFuture.completedFuture(applyDegrade(context, degradePolicy, kind, cause));
        }).thenCompose(it -> it);
    }

    private HttpResponseMessage applyRateLimit(
            GovernanceExecutionContext context,
            RateLimitPolicy policy,
            DegradePolicy degradePolicy
    ) {
        if (!policy.enabled()) {
            return null;
        }
        try {
            if (resolveRateLimitHandler(policy).allow(context, policy, stateStore)) {
                return null;
            }
            context.attributes().put("governance.rate_limited", true);
            return applyDegrade(context, degradePolicy, GovernanceFailureKind.RATE_LIMITED, null);
        } catch (Throwable throwable) {
            return handleAbilityFailure(context, policy, GovernanceFailureKind.GOVERNANCE_ERROR, throwable, degradePolicy);
        }
    }

    private RateLimitPolicyHandler resolveRateLimitHandler(RateLimitPolicy policy) {
        if (rateLimitHandlerLoader == null) {
            return rateLimitHandler;
        }
        String provider = policy.provider();
        if (provider == null || provider.isBlank()) {
            return rateLimitHandler;
        }
        try {
            return rateLimitHandlerLoader.getExtension(provider);
        } catch (IllegalArgumentException ignored) {
            return rateLimitHandler;
        }
    }

    private HttpResponseMessage applyCircuitPreCheck(
            GovernanceExecutionContext context,
            CircuitBreakerPolicy policy,
            DegradePolicy degradePolicy
    ) {
        if (!policy.enabled()) {
            return null;
        }
        try {
            if (circuitBreakerHandler.allow(context, policy, stateStore)) {
                return null;
            }
            context.attributes().put("governance.circuit_open", true);
            return applyDegrade(context, degradePolicy, GovernanceFailureKind.CIRCUIT_OPEN, null);
        } catch (Throwable throwable) {
            return handleAbilityFailure(context, policy, GovernanceFailureKind.GOVERNANCE_ERROR, throwable, degradePolicy);
        }
    }

    private HttpResponseMessage handleAbilityFailure(
            GovernanceExecutionContext context,
            GovernancePolicy policy,
            GovernanceFailureKind kind,
            Throwable cause,
            DegradePolicy degradePolicy
    ) {
        FailureMode mode = failureModeResolver.resolve(policy.ability(), policy.failureMode());
        if (mode == FailureMode.FAIL_OPEN) {
            context.attributes().put("governance." + policy.ability() + ".bypassed", true);
            return null;
        }
        return applyDegrade(context, degradePolicy, kind, cause);
    }

    private HttpResponseMessage applyDegrade(
            GovernanceExecutionContext context,
            DegradePolicy degradePolicy,
            GovernanceFailureKind kind,
            Throwable cause
    ) {
        if (degradePolicy.enabled()) {
            try {
                HttpResponseMessage response = degradeHandler.degrade(context, degradePolicy, kind, cause);
                if (response != null) {
                    context.attributes().put("governance.degraded", true);
                    return response;
                }
            } catch (Throwable ignored) {
                context.attributes().put("governance.degrade.bypassed", true);
            }
        }
        int status = switch (kind) {
            case RATE_LIMITED -> 429;
            case CIRCUIT_OPEN -> 503;
            case TIMEOUT -> 504;
            case RETRY_EXHAUSTED -> 502;
            case GOVERNANCE_ERROR -> 502;
        };
        String message = kind.code();
        return new HttpResponseMessage(
                status,
                Map.of("content-type", "text/plain; charset=UTF-8"),
                message.getBytes(StandardCharsets.UTF_8)
        );
    }

    private GovernanceFailureKind resolveFailureKind(Throwable throwable) {
        if (throwable instanceof GovernanceException ge) {
            return ge.kind();
        }
        return GovernanceFailureKind.RETRY_EXHAUSTED;
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException ce && ce.getCause() != null) {
            return ce.getCause();
        }
        return throwable;
    }

    @SuppressWarnings("unchecked")
    private static <T extends GovernancePolicy> PolicyFactory<T> castFactory(PolicyFactory<?> factory) {
        return (PolicyFactory<T>) factory;
    }
}
