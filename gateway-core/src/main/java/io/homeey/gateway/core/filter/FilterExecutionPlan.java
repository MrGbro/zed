package io.homeey.gateway.core.filter;

import io.homeey.gateway.plugin.api.FilterInvocation;

import java.util.List;

public record FilterExecutionPlan(List<FilterInvocation> invocations) {

    public FilterExecutionPlan {
        invocations = List.copyOf(invocations);
    }
}
