package io.homeey.gateway.core;

import io.homeey.gateway.common.error.ErrorCategory;
import io.homeey.gateway.common.error.GatewayError;
import io.homeey.gateway.core.context.GatewayContext;
import io.homeey.gateway.core.filter.DefaultGatewayFilterChain;
import io.homeey.gateway.plugin.api.FilterFailPolicy;
import io.homeey.gateway.plugin.api.FilterInvocation;
import io.homeey.gateway.plugin.api.GatewayFilter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterChainTest {

    @Test
    void shouldExecuteFiltersInOrder() {
        List<String> order = new ArrayList<>();

        GatewayFilter pre = (ctx, chain) -> {
            order.add("pre");
            return chain.next(ctx);
        };
        GatewayFilter routing = (ctx, chain) -> {
            order.add("routing");
            return chain.next(ctx);
        };
        GatewayFilter post = (ctx, chain) -> {
            order.add("post");
            return chain.next(ctx);
        };

        DefaultGatewayFilterChain chain =
                new DefaultGatewayFilterChain(List.of(
                        new FilterInvocation("pre", pre, FilterFailPolicy.FAIL_CLOSE),
                        new FilterInvocation("routing", routing, FilterFailPolicy.FAIL_CLOSE),
                        new FilterInvocation("post", post, FilterFailPolicy.FAIL_CLOSE)
                ));

        chain.next(new GatewayContext()).toCompletableFuture().join();

        assertEquals(List.of("pre", "routing", "post"), order);
    }

    @Test
    void shouldStopWhenFailCloseFilterThrows() {
        GatewayFilter pre = (ctx, chain) -> chain.next(ctx);
        GatewayFilter failClose = (ctx, chain) -> {
            throw new RuntimeException("boom");
        };
        GatewayFilter post = (ctx, chain) -> {
            ctx.attributes().put("post", true);
            return chain.next(ctx);
        };

        DefaultGatewayFilterChain chain =
                new DefaultGatewayFilterChain(List.of(
                        new FilterInvocation("pre", pre, FilterFailPolicy.FAIL_CLOSE),
                        new FilterInvocation("fail-close", failClose, FilterFailPolicy.FAIL_CLOSE),
                        new FilterInvocation("post", post, FilterFailPolicy.FAIL_CLOSE)
                ));
        GatewayContext context = new GatewayContext();

        CompletionException ex = assertThrows(
                CompletionException.class,
                () -> chain.next(context).toCompletableFuture().join()
        );

        GatewayError gatewayError = (GatewayError) context.attributes().get("gateway.error");
        assertEquals("GW5000", gatewayError.code());
        assertEquals(ErrorCategory.SYSTEM_ERROR, gatewayError.category());
        assertEquals(500, gatewayError.httpStatus());
        assertNull(context.attributes().get("post"));
        assertEquals("boom", ex.getCause().getMessage());
    }

    @Test
    void shouldContinueWhenFailOpenFilterThrows() {
        List<String> order = new ArrayList<>();
        GatewayFilter pre = (ctx, chain) -> {
            order.add("pre");
            return chain.next(ctx);
        };
        GatewayFilter failOpen = (ctx, chain) -> {
            order.add("fail-open");
            throw new RuntimeException("soft-failed");
        };
        GatewayFilter post = (ctx, chain) -> {
            order.add("post");
            return chain.next(ctx);
        };

        DefaultGatewayFilterChain chain = new DefaultGatewayFilterChain(List.of(
                new FilterInvocation("pre", pre, FilterFailPolicy.FAIL_CLOSE),
                new FilterInvocation("fail-open", failOpen, FilterFailPolicy.FAIL_OPEN),
                new FilterInvocation("post", post, FilterFailPolicy.FAIL_CLOSE)
        ));
        GatewayContext context = new GatewayContext();
        chain.next(context).toCompletableFuture().join();

        assertEquals(List.of("pre", "fail-open", "post"), order);
        assertTrue(context.attributes().containsKey("plugin.failopen.fail-open"));
    }
}
