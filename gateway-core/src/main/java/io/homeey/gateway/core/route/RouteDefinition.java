package io.homeey.gateway.core.route;

import java.util.Map;

public record RouteDefinition(
        String id,
        String host,
        String pathPrefix,
        String method,
        Map<String, String> headers
) {
}
