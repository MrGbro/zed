package io.homeey.gateway.admin.model;

import io.homeey.gateway.plugin.api.PluginBinding;
import io.homeey.gateway.plugin.api.PolicySet;

import java.util.List;
import java.util.Map;

public record PublishRequest(
        List<RouteItem> routes,
        List<PluginBinding> pluginBindings,
        PolicySet policySet,
        String operator,
        String summary
) {
    public record RouteItem(
            String id,
            String host,
            String pathPrefix,
            String method,
            Map<String, String> headers,
            String upstreamService,
            String upstreamPath
    ) {
    }
}
