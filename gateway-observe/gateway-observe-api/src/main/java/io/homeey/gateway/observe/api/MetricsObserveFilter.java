package io.homeey.gateway.observe.api;

import io.homeey.gateway.common.spi.Activate;
import io.homeey.gateway.plugin.api.GatewayContext;
import io.homeey.gateway.plugin.api.GatewayFilter;
import io.homeey.gateway.plugin.api.GatewayFilterChain;

import java.util.concurrent.CompletionStage;

/**
 * Metrics filter, currently delegates to the next stage because request-level
 * metrics are recorded by the request observation lifecycle.
 */
@Activate(group = {"global"}, order = -200)
public final class MetricsObserveFilter implements GatewayFilter {
    @Override
    public CompletionStage<Void> filter(GatewayContext context, GatewayFilterChain chain) {
        return chain.next(context);
    }
}
