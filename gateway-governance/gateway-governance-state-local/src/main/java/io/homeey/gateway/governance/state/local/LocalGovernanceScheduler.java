package io.homeey.gateway.governance.state.local;

import io.homeey.gateway.governance.api.GovernanceException;
import io.homeey.gateway.governance.api.GovernanceFailureKind;
import io.homeey.gateway.governance.api.GovernanceScheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class LocalGovernanceScheduler implements GovernanceScheduler {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gateway-governance-scheduler");
        t.setDaemon(true);
        return t;
    });

    @Override
    public <T> CompletionStage<T> withTimeout(CompletionStage<T> origin, long timeoutMillis, String message) {
        if (timeoutMillis <= 0L) {
            return origin;
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        scheduler.schedule(() -> result.completeExceptionally(new GovernanceException(
                GovernanceFailureKind.TIMEOUT,
                message == null || message.isBlank() ? "request timeout" : message
        )), timeoutMillis, TimeUnit.MILLISECONDS);

        origin.whenComplete((value, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(throwable);
                return;
            }
            result.complete(value);
        });
        return result;
    }

    @Override
    public CompletionStage<Void> delay(long delayMillis) {
        if (delayMillis <= 0L) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        scheduler.schedule(() -> result.complete(null), delayMillis, TimeUnit.MILLISECONDS);
        return result;
    }
}
