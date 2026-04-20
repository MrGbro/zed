package io.homeey.gateway.governance.engine;

import io.homeey.gateway.common.spi.Activate;
import io.homeey.gateway.common.spi.ExtensionLoader;
import io.homeey.gateway.governance.api.GovernanceEngine;
import io.homeey.gateway.governance.api.GovernanceExecutionContext;
import io.homeey.gateway.governance.api.TrafficGovernanceGatewayFilter;
import io.homeey.gateway.plugin.api.GatewayContext;
import io.homeey.gateway.plugin.api.GatewayFilterChain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

@Activate(group = {"route"}, order = -60, conditions = {"governance.enabled=true"})
public final class DefaultTrafficGovernanceFilter extends TrafficGovernanceGatewayFilter {
    private final GovernanceEngine governanceEngine;

    public DefaultTrafficGovernanceFilter() {
        this(ExtensionLoader.getExtensionLoader(GovernanceEngine.class).getDefaultExtension());
    }

    DefaultTrafficGovernanceFilter(GovernanceEngine governanceEngine) {
        this.governanceEngine = governanceEngine;
    }

    @Override
    public CompletionStage<Void> filter(GatewayContext context, GatewayFilterChain chain) {
        Map<String, Object> entries = routePolicyEntries(context);
        GovernanceExecutionContext executionContext = new GovernanceExecutionContext(context, entries);
        return governanceEngine.execute(
                executionContext,
                () -> chain.next(context).thenApply(unused -> context.response())
        ).thenAccept(context::response);
    }

    private Map<String, Object> routePolicyEntries(GatewayContext context) {
        Object value = context.attributes().get("route.policySet");
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    converted.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return Map.copyOf(converted);
        }
        return Map.of();
    }
}
