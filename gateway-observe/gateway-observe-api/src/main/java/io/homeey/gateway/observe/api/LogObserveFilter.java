package io.homeey.gateway.observe.api;

import io.homeey.gateway.common.spi.Activate;
import io.homeey.gateway.plugin.api.GatewayContext;
import io.homeey.gateway.plugin.api.GatewayFilter;
import io.homeey.gateway.plugin.api.GatewayFilterChain;

import java.util.concurrent.CompletionStage;

/**
 * Log filter kept in chain to reserve log stage ordering.
 */
@Activate(group = {"global"}, order = -100)
public final class LogObserveFilter implements GatewayFilter {
    @Override
    public CompletionStage<Void> filter(GatewayContext context, GatewayFilterChain chain) {
        return chain.next(context);
    }
}
