package io.homeey.gateway.governance.local;

import io.homeey.gateway.common.spi.Activate;
import io.homeey.gateway.common.spi.ExtensionLoader;
import io.homeey.gateway.governance.api.GovernanceExecutor;
import io.homeey.gateway.governance.api.GovernancePolicy;
import io.homeey.gateway.governance.api.GovernancePolicyParser;
import io.homeey.gateway.plugin.api.GatewayContext;
import io.homeey.gateway.plugin.api.GatewayFilterChain;
import io.homeey.gateway.governance.api.TrafficGovernanceGatewayFilter;
import io.homeey.gateway.transport.api.HttpResponseMessage;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Concrete route governance filter implementation, activated by route policy key.
 */
@Activate(group = {"route"}, order = -60, conditions = {"governance.enabled=true"})
public final class LocalTrafficGovernanceFilter extends TrafficGovernanceGatewayFilter {
    private final GovernancePolicyParser parser;
    private final GovernanceExecutor executor;

    public LocalTrafficGovernanceFilter() {
        this(
                ExtensionLoader.getExtensionLoader(GovernancePolicyParser.class).getExtension("local"),
                ExtensionLoader.getExtensionLoader(GovernanceExecutor.class).getExtension("local")
        );
    }

    LocalTrafficGovernanceFilter(GovernancePolicyParser parser, GovernanceExecutor executor) {
        this.parser = parser;
        this.executor = executor;
    }

    @Override
    public CompletionStage<Void> filter(GatewayContext context, GatewayFilterChain chain) {
        Map<String, Object> entries = routePolicyEntries(context);
        GovernancePolicy policy = parser.parse(entries);
        if (!policy.enabled()) {
            return chain.next(context);
        }

        return executor.execute(
                context,
                policy,
                () -> chain.next(context).thenApply(unused -> toProxyLike(context.response()))
        ).thenAccept(context::response);
    }

    private Map<String, Object> routePolicyEntries(GatewayContext context) {
        Object value = context.attributes().get("route.policySet");
        if (value instanceof Map<?, ?> map) {
            java.util.LinkedHashMap<String, Object> converted = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return Map.copyOf(converted);
        }
        return Map.of();
    }

    private io.homeey.gateway.proxy.api.ProxyResponse toProxyLike(HttpResponseMessage response) {
        if (response == null) {
            return new io.homeey.gateway.proxy.api.ProxyResponse(
                    502,
                    Map.of("content-type", "text/plain; charset=UTF-8"),
                    "upstream error".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        }
        return new io.homeey.gateway.proxy.api.ProxyResponse(
                response.statusCode(),
                response.headers() == null ? Map.of() : response.headers(),
                response.body() == null ? new byte[0] : response.body()
        );
    }
}
