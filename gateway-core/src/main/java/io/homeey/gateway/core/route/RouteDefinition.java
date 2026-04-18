package io.homeey.gateway.core.route;

import io.homeey.gateway.plugin.api.PluginBinding;
import io.homeey.gateway.plugin.api.PolicySet;

import java.util.List;
import java.util.Map;

public record RouteDefinition(
        String id,
        String host,
        String pathPrefix,
        String method,
        Map<String, String> headers,
        String upstreamService,
        String upstreamPath,
        List<PluginBinding> pluginBindings,
        PolicySet policySet
) {
    public RouteDefinition {
        pluginBindings = pluginBindings == null ? List.of() : List.copyOf(pluginBindings);
        policySet = policySet == null ? new PolicySet(Map.of()) : policySet;
    }
}
