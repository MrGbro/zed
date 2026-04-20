package io.homeey.gateway.governance.retry.local;

import io.homeey.gateway.governance.api.GovernanceException;
import io.homeey.gateway.governance.api.GovernanceExecutionContext;
import io.homeey.gateway.governance.api.GovernanceFailureKind;
import io.homeey.gateway.governance.api.GovernanceScheduler;
import io.homeey.gateway.governance.api.RetryPolicy;
import io.homeey.gateway.governance.api.RetryPolicyHandler;
import io.homeey.gateway.transport.api.HttpResponseMessage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class LocalRetryPolicyHandler implements RetryPolicyHandler {
    @Override
    public CompletionStage<HttpResponseMessage> execute(
            GovernanceExecutionContext context,
            RetryPolicy policy,
            Supplier<CompletionStage<HttpResponseMessage>> attempt,
            GovernanceScheduler scheduler
    ) {
        int maxAttempts = policy.enabled() ? policy.maxAttempts() : 1;
        return runAttempt(context, policy, attempt, scheduler, 1, maxAttempts);
    }

    private CompletionStage<HttpResponseMessage> runAttempt(
            GovernanceExecutionContext context,
            RetryPolicy policy,
            Supplier<CompletionStage<HttpResponseMessage>> attempt,
            GovernanceScheduler scheduler,
            int current,
            int maxAttempts
    ) {
        context.attributes().put("governance.retry.attempts", current);
        CompletionStage<HttpResponseMessage> stage;
        try {
            stage = attempt.get();
        } catch (Throwable throwable) {
            stage = CompletableFuture.failedFuture(throwable);
        }

        return stage.handle((response, throwable) -> {
            Throwable cause = unwrap(throwable);
            boolean shouldRetry = shouldRetry(policy, response, cause);
            if (!shouldRetry) {
                if (cause == null) {
                    return CompletableFuture.completedFuture(response);
                }
                CompletableFuture<HttpResponseMessage> failed = new CompletableFuture<>();
                failed.completeExceptionally(cause);
                return failed;
            }
            if (current >= maxAttempts) {
                CompletableFuture<HttpResponseMessage> failed = new CompletableFuture<>();
                failed.completeExceptionally(new GovernanceException(
                        GovernanceFailureKind.RETRY_EXHAUSTED,
                        cause == null ? "retry exhausted" : cause.getMessage(),
                        cause
                ));
                return failed;
            }
            context.attributes().put("governance.retry", true);
            return scheduler.delay(policy.backoffMillis())
                    .thenCompose(unused -> runAttempt(context, policy, attempt, scheduler, current + 1, maxAttempts));
        }).thenCompose(it -> it);
    }

    private boolean shouldRetry(RetryPolicy policy, HttpResponseMessage response, Throwable throwable) {
        if (!policy.enabled()) {
            return false;
        }
        if (throwable != null) {
            if (throwable instanceof GovernanceException ge && ge.kind() == GovernanceFailureKind.TIMEOUT) {
                return policy.retryOnTimeout();
            }
            return true;
        }
        return response != null && policy.retryOnStatuses().contains(response.statusCode());
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException ce && ce.getCause() != null) {
            return ce.getCause();
        }
        return throwable;
    }
}
