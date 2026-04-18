package io.homeey.gateway.plugin.api;

import java.util.concurrent.CompletionStage;

public interface GatewayFilterChain {
    CompletionStage<Void> next(GatewayContext context);
}
