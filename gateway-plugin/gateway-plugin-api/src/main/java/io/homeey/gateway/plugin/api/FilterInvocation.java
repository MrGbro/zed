package io.homeey.gateway.plugin.api;

import java.util.Objects;

public record FilterInvocation(
        String name,
        GatewayFilter filter,
        FilterFailPolicy failPolicy
) {
    public FilterInvocation {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(failPolicy, "failPolicy");
    }
}
