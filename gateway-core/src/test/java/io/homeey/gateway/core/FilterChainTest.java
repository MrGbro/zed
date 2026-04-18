package io.homeey.gateway.core;

import io.homeey.gateway.common.error.ErrorCategory;
import io.homeey.gateway.common.error.GatewayError;
import io.homeey.gateway.core.context.GatewayContext;
import io.homeey.gateway.core.filter.DefaultGatewayFilterChain;
import io.homeey.gateway.plugin.api.GatewayFilter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
                new DefaultGatewayFilterChain(List.of(pre, routing, post));

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
                new DefaultGatewayFilterChain(List.of(pre, failClose, post));
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
}
