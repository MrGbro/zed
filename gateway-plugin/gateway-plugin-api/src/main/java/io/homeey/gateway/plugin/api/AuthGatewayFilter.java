package io.homeey.gateway.plugin.api;

import io.homeey.gateway.common.spi.Activate;

import java.util.concurrent.CompletionStage;

@Activate(group = {"route"}, order = 20, conditions = {"auth.enabled=true"})
public final class AuthGatewayFilter implements GatewayFilter {
    @Override
    public CompletionStage<Void> filter(GatewayContext context, GatewayFilterChain chain) {
        context.attributes().put("plugin.auth.executed", true);
        return chain.next(context);
    }
}
