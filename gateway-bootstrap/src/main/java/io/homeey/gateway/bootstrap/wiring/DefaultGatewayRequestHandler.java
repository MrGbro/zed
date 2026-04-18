package io.homeey.gateway.bootstrap.wiring;

import io.homeey.gateway.core.route.RadixRouteLocator;
import io.homeey.gateway.core.route.RouteDefinition;
import io.homeey.gateway.core.filter.DefaultGatewayFilterChain;
import io.homeey.gateway.core.filter.FilterExecutionPlan;
import io.homeey.gateway.core.filter.FilterExecutionPlanCompiler;
import io.homeey.gateway.core.runtime.RuntimeSnapshotManager;
import io.homeey.gateway.proxy.api.ProxyClient;
import io.homeey.gateway.proxy.api.ProxyRequest;
import io.homeey.gateway.registry.api.ServiceDiscoveryProvider;
import io.homeey.gateway.transport.api.GatewayRequestHandler;
import io.homeey.gateway.transport.api.HttpRequestMessage;
import io.homeey.gateway.transport.api.HttpResponseMessage;
import io.homeey.gateway.plugin.api.GatewayContext;
import io.homeey.gateway.plugin.api.PluginBinding;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

public final class DefaultGatewayRequestHandler implements GatewayRequestHandler {
    private final RuntimeSnapshotManager snapshotManager;
    private final ServiceDiscoveryProvider discoveryProvider;
    private final ProxyClient proxyClient;
    private final FilterExecutionPlanCompiler filterPlanCompiler;
    private final AtomicInteger rr = new AtomicInteger(0);

    public DefaultGatewayRequestHandler(
            RuntimeSnapshotManager snapshotManager,
            ServiceDiscoveryProvider discoveryProvider,
            ProxyClient proxyClient
    ) {
        this.snapshotManager = snapshotManager;
        this.discoveryProvider = discoveryProvider;
        this.proxyClient = proxyClient;
        this.filterPlanCompiler = new FilterExecutionPlanCompiler();
    }

    @Override
    public CompletionStage<HttpResponseMessage> handle(HttpRequestMessage request) {
        String host = normalizedHost(request.host());
        RadixRouteLocator locator = new RadixRouteLocator(snapshotManager.currentRouteSnapshot());
        Optional<RouteDefinition> match = locator.locate(host, request.path(), request.method(), request.headers());
        if (match.isEmpty()) {
            return CompletableFuture.completedFuture(textResponse(404, "route not found"));
        }
        RouteDefinition route = match.get();
        GatewayContext context = new GatewayContext()
                .request(request)
                .routeId(route.id());
        FilterExecutionPlan plan = compilePlan(route);
        DefaultGatewayFilterChain chain = new DefaultGatewayFilterChain(plan.invocations());
        return chain.next(context)
                .thenCompose(unused -> doForward(route, request))
                .exceptionally(ex -> {
                    Object err = context.attributes().get("gateway.error");
                    if (err != null) {
                        return textResponse(500, "plugin execution failed");
                    }
                    return textResponse(502, "upstream error");
                });
    }

    private String pickRoundRobin(List<String> instances) {
        int index = Math.floorMod(rr.getAndIncrement(), instances.size());
        return instances.get(index);
    }

    private String normalizedHost(String host) {
        if (host == null) {
            return "";
        }
        int idx = host.indexOf(':');
        return idx > 0 ? host.substring(0, idx) : host;
    }

    private String withQuery(String query) {
        return query == null || query.isBlank() ? "" : "?" + query;
    }

    private boolean isDirectEndpoint(String value) {
        return value.contains(":");
    }

    private CompletionStage<HttpResponseMessage> forwardToEndpoint(
            String endpoint,
            RouteDefinition route,
            HttpRequestMessage request
    ) {
        String upstreamPath = route.upstreamPath() == null || route.upstreamPath().isBlank()
                ? request.path()
                : route.upstreamPath();
        String url = "http://" + endpoint + upstreamPath + withQuery(request.query());
        ProxyRequest proxyRequest = new ProxyRequest(
                request.method(),
                url,
                request.headers(),
                request.body()
        );
        return proxyClient.execute(proxyRequest)
                .thenApply(proxyResponse -> new HttpResponseMessage(
                        proxyResponse.statusCode(),
                        proxyResponse.headers(),
                        proxyResponse.body()
                ));
    }

    private CompletionStage<HttpResponseMessage> doForward(RouteDefinition route, HttpRequestMessage request) {
        if (route.upstreamService() == null || route.upstreamService().isBlank()) {
            return CompletableFuture.completedFuture(textResponse(503, "upstream unavailable"));
        }
        if (isDirectEndpoint(route.upstreamService())) {
            return forwardToEndpoint(route.upstreamService(), route, request);
        }
        return discoveryProvider.getInstances(route.upstreamService())
                .thenCompose(instances -> {
                    if (instances == null || instances.isEmpty()) {
                        return CompletableFuture.completedFuture(textResponse(503, "upstream unavailable"));
                    }
                    return forwardToEndpoint(pickRoundRobin(instances), route, request);
                });
    }

    private FilterExecutionPlan compilePlan(RouteDefinition route) {
        List<PluginBinding> bindings = route.pluginBindings() == null ? List.of() : route.pluginBindings();
        return filterPlanCompiler.compile(
                route.id(),
                bindings,
                route.policySet() == null ? Map.of() : route.policySet().entries()
        );
    }

    private HttpResponseMessage textResponse(int status, String text) {
        return new HttpResponseMessage(
                status,
                Map.of("content-type", "text/plain; charset=UTF-8"),
                text.getBytes(StandardCharsets.UTF_8)
        );
    }
}
