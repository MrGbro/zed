package io.homeey.gateway.governance.api;

import io.homeey.gateway.plugin.api.GatewayFilter;

/**
 * Marker base type for governance filters. Concrete modules provide activated implementations.
 */
public abstract class TrafficGovernanceGatewayFilter implements GatewayFilter {
}
