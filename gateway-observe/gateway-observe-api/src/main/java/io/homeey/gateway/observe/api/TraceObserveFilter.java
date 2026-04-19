package io.homeey.gateway.observe.api;

import io.homeey.gateway.common.spi.Activate;
import io.homeey.gateway.plugin.api.GatewayContext;
import io.homeey.gateway.plugin.api.GatewayFilter;
import io.homeey.gateway.plugin.api.GatewayFilterChain;

import java.util.concurrent.CompletionStage;

/**
 * Trace filter that ensures trace id exists and is attached to context.
 */
@Activate(group = {"global"}, order = -300)
public final class TraceObserveFilter implements GatewayFilter {
    @Override
    public CompletionStage<Void> filter(GatewayContext context, GatewayFilterChain chain) {
        String traceId = context.traceId();
        if (traceId == null) {
            traceId = "";
            context.traceId(traceId);
        }
        return chain.next(context);
    }
}
