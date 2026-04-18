package io.homeey.gateway.core.route;

import java.util.List;

public final class RouteTableSnapshot {
    private final String version;
    private final List<RouteDefinition> routes;

    public RouteTableSnapshot(String version, List<RouteDefinition> routes) {
        this.version = version;
        this.routes = List.copyOf(routes);
    }

    public String version() {
        return version;
    }

    public List<RouteDefinition> routes() {
        return routes;
    }
}
