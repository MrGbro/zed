package io.homeey.gateway.core.filter;

import io.homeey.gateway.plugin.api.GatewayFilter;

import java.util.List;

public record FilterExecutionPlan(List<GatewayFilter> filters) {

    public FilterExecutionPlan {
        filters = List.copyOf(filters);
    }
}
