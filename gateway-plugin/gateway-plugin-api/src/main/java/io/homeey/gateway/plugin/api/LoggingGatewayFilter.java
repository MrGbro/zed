package io.homeey.gateway.plugin.api;

import io.homeey.gateway.common.spi.Activate;

import java.util.concurrent.CompletionStage;

@Activate(group = {"global"}, order = 10)
public final class LoggingGatewayFilter implements GatewayFilter {
    @Override
    public CompletionStage<Void> filter(GatewayContext context, GatewayFilterChain chain) {
        context.attributes().put("plugin.logging.executed", true);
        return chain.next(context);
    }
}
