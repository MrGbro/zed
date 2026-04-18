package io.homeey.gateway.core.filter;

import io.homeey.gateway.common.error.ErrorCategory;
import io.homeey.gateway.common.error.GatewayError;
import io.homeey.gateway.plugin.api.FilterFailPolicy;
import io.homeey.gateway.plugin.api.FilterInvocation;
import io.homeey.gateway.plugin.api.GatewayContext;
import io.homeey.gateway.plugin.api.GatewayFilterChain;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * 默认网关过滤器链实现，按顺序执行过滤器。
 * <p>
 * 该链支持过滤器的失败策略（FAIL_CLOSE/FAIL_OPEN），当过滤器执行失败时：
 * <ul>
 *   <li>FAIL_CLOSE：中断链执行，返回错误</li>
 *   <li>FAIL_OPEN：跳过当前过滤器，继续执行下一个</li>
 * </ul>
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public final class DefaultGatewayFilterChain implements GatewayFilterChain {
    private final List<FilterInvocation> invocations;
    private final int index;

    public DefaultGatewayFilterChain(List<FilterInvocation> invocations) {
        this(invocations, 0);
    }

    private DefaultGatewayFilterChain(List<FilterInvocation> invocations, int index) {
        this.invocations = List.copyOf(invocations);
        this.index = index;
    }

    @Override
    public CompletionStage<Void> next(GatewayContext context) {
        if (index >= invocations.size()) {
            return CompletableFuture.completedFuture(null);
        }

        FilterInvocation current = invocations.get(index);
        DefaultGatewayFilterChain next = new DefaultGatewayFilterChain(invocations, index + 1);

        try {
            return current.filter().filter(context, next)
                    .handle((unused, ex) -> {
                        if (ex == null) {
                            return CompletableFuture.<Void>completedFuture(null);
                        }
                        Throwable cause = unwrap(ex);
                        return handleThrowable(context, current, next, cause);
                    })
                    .thenCompose(Function.identity());
        } catch (Throwable throwable) {
            return handleThrowable(context, current, next, throwable);
        }
    }

    private CompletionStage<Void> handleThrowable(GatewayContext context,
                                                  FilterInvocation current,
                                                  DefaultGatewayFilterChain next,
                                                  Throwable throwable) {
        if (current.failPolicy() == FilterFailPolicy.FAIL_OPEN) {
            context.attributes().put("plugin.failopen." + current.name(), throwable.getMessage());
            return next(context);
        }
        mapGatewayError(context, throwable);
        CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(throwable);
        return failed;
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
