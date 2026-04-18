package io.homeey.gateway.core.filter;

import io.homeey.gateway.common.error.ErrorCategory;
import io.homeey.gateway.common.error.GatewayError;
import io.homeey.gateway.plugin.api.GatewayContext;
import io.homeey.gateway.plugin.api.GatewayFilter;
import io.homeey.gateway.plugin.api.GatewayFilterChain;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public final class DefaultGatewayFilterChain implements GatewayFilterChain {
    private final List<GatewayFilter> filters;
    private final int index;

    public DefaultGatewayFilterChain(List<GatewayFilter> filters) {
        this(filters, 0);
    }

    private DefaultGatewayFilterChain(List<GatewayFilter> filters, int index) {
        this.filters = List.copyOf(filters);
        this.index = index;
    }

    @Override
    public CompletionStage<Void> next(GatewayContext context) {
        if (index >= filters.size()) {
            return CompletableFuture.completedFuture(null);
        }

        GatewayFilter current = filters.get(index);
        DefaultGatewayFilterChain next = new DefaultGatewayFilterChain(filters, index + 1);

        try {
            return current.filter(context, next)
                    .exceptionally(ex -> {
                        Throwable cause = unwrap(ex);
                        mapGatewayError(context, cause);
                        throw new CompletionException(cause);
                    });
        } catch (Throwable throwable) {
            mapGatewayError(context, throwable);
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(throwable);
            return failed;
        }
    }

    private static void mapGatewayError(GatewayContext context, Throwable throwable) {
        GatewayError error = new GatewayError(
                "GW5000",
                ErrorCategory.SYSTEM_ERROR,
                500,
                false,
                throwable.getMessage(),
                null
        );
        context.attributes().put("gateway.error", error);
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }
}
