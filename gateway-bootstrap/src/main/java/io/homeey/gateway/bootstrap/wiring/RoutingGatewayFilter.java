package io.homeey.gateway.bootstrap.wiring;

import io.homeey.gateway.common.spi.Activate;
import io.homeey.gateway.observe.api.ObserveKeys;
import io.homeey.gateway.observe.api.RequestObservation;
import io.homeey.gateway.plugin.api.GatewayContext;
import io.homeey.gateway.plugin.api.GatewayFilter;
import io.homeey.gateway.plugin.api.GatewayFilterChain;
import io.homeey.gateway.proxy.api.ProxyClient;
import io.homeey.gateway.proxy.api.ProxyRequest;
import io.homeey.gateway.registry.api.ServiceDiscoveryProvider;
import io.homeey.gateway.transport.api.HttpRequestMessage;
import io.homeey.gateway.transport.api.HttpResponseMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Routing filter executes upstream forwarding as chain terminal behavior.
 */
@Activate(group = {"route"}, order = 10)
public final class RoutingGatewayFilter implements GatewayFilter {
    private final ServiceDiscoveryProvider discoveryProvider;
    private final ProxyClient proxyClient;
    private final AtomicInteger rr = new AtomicInteger(0);

    public RoutingGatewayFilter(ServiceDiscoveryProvider discoveryProvider, ProxyClient proxyClient) {
        this.discoveryProvider = discoveryProvider;
        this.proxyClient = proxyClient;
    }

    @Override
    public CompletionStage<Void> filter(GatewayContext context, GatewayFilterChain chain) {
        String routePathPrefix = String.valueOf(context.attributes().get("route.pathPrefix"));
        String routeMethod = String.valueOf(context.attributes().get("route.method"));
        if (routeMethod == null || routeMethod.isBlank() || "null".equals(routeMethod)) {
            routeMethod = context.request().method();
        }
        if ("static".equalsIgnoreCase(String.valueOf(context.attributes().get("upstream.service")))) {
            return chain.next(context);
        }
        if (routePathPrefix != null && !routePathPrefix.isBlank() && !"null".equals(routePathPrefix)) {
            String reqPath = context.request().path();
            if (reqPath == null || !reqPath.startsWith(routePathPrefix)) {
                return chain.next(context);
            }
        }
        if (!routeMethod.equalsIgnoreCase(context.request().method())) {
            return chain.next(context);
        }

        String upstreamService = String.valueOf(context.attributes().get("upstream.service"));
        if (upstreamService == null || upstreamService.isBlank() || "null".equals(upstreamService)) {
            context.response(new HttpResponseMessage(
                    503,
                    Map.of("content-type", "text/plain; charset=UTF-8"),
                    "upstream unavailable".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            ));
            return CompletableFuture.completedFuture(null);
        }
        HttpRequestMessage request = context.request();
        String upstreamPathAttr = String.valueOf(context.attributes().get("upstream.path"));
        final String upstreamPath = (upstreamPathAttr == null || upstreamPathAttr.isBlank() || "null".equals(upstreamPathAttr))
                ? request.path()
                : upstreamPathAttr;

        if (isDirectEndpoint(upstreamService)) {
            return forwardToEndpoint(context, upstreamService, upstreamPath, request);
        }

        return discoveryProvider.getInstances(upstreamService)
                .thenCompose(instances -> {
                    if (instances == null || instances.isEmpty()) {
                        context.response(new HttpResponseMessage(
                                503,
                                Map.of("content-type", "text/plain; charset=UTF-8"),
                                "upstream unavailable".getBytes(java.nio.charset.StandardCharsets.UTF_8)
                        ));
                        return CompletableFuture.completedFuture(null);
                    }
                    String endpoint = pickRoundRobin(instances);
                    return forwardToEndpoint(context, endpoint, upstreamPath, request);
                });
    }

    private CompletionStage<Void> forwardToEndpoint(
            GatewayContext context,
            String endpoint,
            String upstreamPath,
            HttpRequestMessage request
    ) {
        RequestObservation observation = (RequestObservation) context.attributes().get(ObserveKeys.REQUEST_OBSERVATION);
        if (observation != null) {
            observation.onUpstreamSelected(endpoint);
        }
        String url = "http://" + endpoint + upstreamPath + withQuery(request.query());
        ProxyRequest proxyRequest = new ProxyRequest(
                request.method(),
                url,
                request.headers(),
                request.body()
        );
        return proxyClient.execute(proxyRequest)
                .thenAccept(proxyResponse -> context.response(new HttpResponseMessage(
                        proxyResponse.statusCode(),
                        proxyResponse.headers(),
                        proxyResponse.body()
                )));
    }

    private String pickRoundRobin(List<String> instances) {
        int index = Math.floorMod(rr.getAndIncrement(), instances.size());
        return instances.get(index);
    }

    private boolean isDirectEndpoint(String value) {
        return value.contains(":");
    }

    private String withQuery(String query) {
        return query == null || query.isBlank() ? "" : "?" + query;
    }
}
