package io.homeey.gateway.governance.api;

import io.homeey.gateway.common.context.Attributes;
import io.homeey.gateway.plugin.api.GatewayContext;

import java.util.Map;
import java.util.Objects;

public final class GovernanceExecutionContext {
    private final GatewayContext gatewayContext;
    private final Map<String, Object> policyEntries;

    public GovernanceExecutionContext(GatewayContext gatewayContext, Map<String, Object> policyEntries) {
        this.gatewayContext = Objects.requireNonNull(gatewayContext, "gatewayContext");
        this.policyEntries = policyEntries == null ? Map.of() : Map.copyOf(policyEntries);
    }

    public GatewayContext gatewayContext() {
        return gatewayContext;
    }

    public Attributes attributes() {
        return gatewayContext.attributes();
    }

    public String routeId() {
        return gatewayContext.routeId();
    }

    public Map<String, Object> policyEntries() {
        return policyEntries;
    }
}
