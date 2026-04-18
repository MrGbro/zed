package io.homeey.gateway.plugin.api;

import io.homeey.gateway.common.spi.SPI;

import java.util.concurrent.CompletionStage;

@SPI
public interface GatewayFilter {
    default void init() {
        // no-op
    }

    default void start() {
        // no-op
    }

    default void stop() {
        // no-op
    }

    CompletionStage<Void> filter(GatewayContext context, GatewayFilterChain chain);
}
