package io.homeey.gateway.plugin.api;

import java.util.concurrent.CompletionStage;

public interface GatewayFilter {
    CompletionStage<Void> filter(GatewayContext context, GatewayFilterChain chain);
}
