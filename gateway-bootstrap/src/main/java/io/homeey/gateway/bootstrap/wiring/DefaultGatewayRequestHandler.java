package io.homeey.gateway.bootstrap.wiring;

import io.homeey.gateway.core.route.RadixRouteLocator;
import io.homeey.gateway.core.route.RouteDefinition;
import io.homeey.gateway.core.filter.DefaultGatewayFilterChain;
import io.homeey.gateway.core.filter.FilterExecutionPlan;
import io.homeey.gateway.core.filter.FilterExecutionPlanCompiler;
import io.homeey.gateway.core.runtime.RuntimeSnapshotManager;
import io.homeey.gateway.observe.api.ObserveKeys;
import io.homeey.gateway.observe.api.ObserveProvider;
import io.homeey.gateway.observe.api.RequestObservation;
import io.homeey.gateway.proxy.api.ProxyClient;
import io.homeey.gateway.registry.api.ServiceDiscoveryProvider;
import io.homeey.gateway.transport.api.GatewayRequestHandler;
import io.homeey.gateway.transport.api.HttpRequestMessage;
import io.homeey.gateway.transport.api.HttpResponseMessage;
import io.homeey.gateway.plugin.api.GatewayContext;
import io.homeey.gateway.plugin.api.PluginBinding;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 默认网关请求处理器，实现网关的核心请求处理逻辑。
 * <p>
 * 该处理器负责：
 * <ul>
 *   <li>根据请求匹配路由</li>
 *   <li>执行过滤器链</li>
 *   <li>服务发现与负载均衡</li>
 *   <li>代理转发到上游服务</li>
 * </ul>
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public final class DefaultGatewayRequestHandler implements GatewayRequestHandler {
    private final RuntimeSnapshotManager snapshotManager;
    private final ServiceDiscoveryProvider discoveryProvider;
    private final ProxyClient proxyClient;
    private final ObserveProvider observeProvider;
    private final FilterExecutionPlanCompiler filterPlanCompiler;
    private final Path staticResourcesRoot;
    private final String metricsPath;
    private final RoutingGatewayFilter routingGatewayFilter;

    public DefaultGatewayRequestHandler(
            RuntimeSnapshotManager snapshotManager,
            ServiceDiscoveryProvider discoveryProvider,
            ProxyClient proxyClient
    ) {
        this(snapshotManager, discoveryProvider, proxyClient, "static", null, "/metrics");
    }

    public DefaultGatewayRequestHandler(
            RuntimeSnapshotManager snapshotManager,
            ServiceDiscoveryProvider discoveryProvider,
            ProxyClient proxyClient,
            String staticResourcesDir
    ) {
        this(snapshotManager, discoveryProvider, proxyClient, staticResourcesDir, null, "/metrics");
    }

    public DefaultGatewayRequestHandler(
            RuntimeSnapshotManager snapshotManager,
            ServiceDiscoveryProvider discoveryProvider,
            ProxyClient proxyClient,
            String staticResourcesDir,
            ObserveProvider observeProvider,
            String metricsPath
    ) {
        this.snapshotManager = snapshotManager;
        this.discoveryProvider = discoveryProvider;
        this.proxyClient = proxyClient;
        this.observeProvider = observeProvider;
        this.filterPlanCompiler = new FilterExecutionPlanCompiler();
        this.routingGatewayFilter = new RoutingGatewayFilter(discoveryProvider, proxyClient);
        this.staticResourcesRoot = Path.of(staticResourcesDir == null || staticResourcesDir.isBlank() ? "static" : staticResourcesDir)
                .toAbsolutePath()
                .normalize();
        this.metricsPath = metricsPath == null || metricsPath.isBlank() ? "/metrics" : (metricsPath.startsWith("/") ? metricsPath : "/" + metricsPath);
    }

    @Override
    public CompletionStage<HttpResponseMessage> handle(HttpRequestMessage request) {
        if ("GET".equalsIgnoreCase(request.method()) && this.metricsPath.equals(request.path())) {
            String payload = observeProvider == null ? "" : observeProvider.metricsSnapshot();
            return CompletableFuture.completedFuture(RequestObservation.metricsResponse(payload));
        }
        RequestObservation observation = observeProvider == null ? RequestObservation.noop(request) : observeProvider.begin(request);
        String traceId = observation.traceId();
        String host = normalizedHost(request.host());
        RadixRouteLocator locator = new RadixRouteLocator(snapshotManager.currentRouteSnapshot());
        Optional<RouteDefinition> match = locator.locate(host, request.path(), request.method(), request.headers());
        if (match.isEmpty()) {
            HttpResponseMessage response = withTraceHeader(textResponse(404, "route not found"), traceId);
            observation.onResponse(response);
            observation.close();
            return CompletableFuture.completedFuture(response);
        }
        RouteDefinition route = match.get();
        observation.onRouteMatched(route.id());
        GatewayContext context = new GatewayContext()
                .request(request)
                .routeId(route.id())
                .traceId(traceId);
        context.attributes().put(ObserveKeys.REQUEST_OBSERVATION, observation);
        context.attributes().put("upstream.service", route.upstreamService());
        context.attributes().put("upstream.path", route.upstreamPath());
        context.attributes().put("route.pathPrefix", route.pathPrefix());
        context.attributes().put("route.method", route.method());
        context.attributes().put("route.policySet", route.policySet() == null ? Map.of() : route.policySet().entries());
        FilterExecutionPlan plan = compilePlan(route);
        DefaultGatewayFilterChain chain = new DefaultGatewayFilterChain(plan.invocations());
        return chain.next(context)
                .thenApply(response -> {
                    HttpResponseMessage forwarded = context.response();
                    if (forwarded == null) {
                        if ("static".equalsIgnoreCase(route.upstreamService())) {
                            forwarded = serveStatic(route, request);
                        } else {
                            forwarded = textResponse(502, "upstream error");
                        }
                    }
                    HttpResponseMessage withTrace = withTraceHeader(forwarded, traceId);
                    observation.onResponse(withTrace);
                    return withTrace;
                })
                .exceptionally(ex -> {
                    Object err = context.attributes().get("gateway.error");
                    observation.onError(ex, err != null ? "plugin" : "upstream");
                    if (err != null) {
                        return withTraceHeader(textResponse(500, "plugin execution failed"), traceId);
                    }
                    return withTraceHeader(textResponse(502, "upstream error"), traceId);
                })
                .whenComplete((response, throwable) -> observation.close());
    }

    private String normalizedHost(String host) {
        if (host == null) {
            return "";
        }
        int idx = host.indexOf(':');
        return idx > 0 ? host.substring(0, idx) : host;
    }

    private HttpResponseMessage serveStatic(RouteDefinition route, HttpRequestMessage request) {
        if (!"GET".equalsIgnoreCase(request.method()) && !"HEAD".equalsIgnoreCase(request.method())) {
            return textResponse(405, "method not allowed");
        }
        String relativePath = resolveStaticRelativePath(route, request.path());
        if (relativePath.isBlank()) {
            relativePath = "index.html";
        }
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        Path target = staticResourcesRoot.resolve(relativePath).normalize();
        if (!target.startsWith(staticResourcesRoot)) {
            return textResponse(403, "forbidden");
        }
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return textResponse(404, "static resource not found");
        }
        try {
            byte[] body = "HEAD".equalsIgnoreCase(request.method()) ? new byte[0] : Files.readAllBytes(target);
            return new HttpResponseMessage(
                    200,
                    Map.of("content-type", guessContentType(target)),
                    body
            );
        } catch (IOException ex) {
            return textResponse(500, "failed to read static resource");
        }
    }

    private String resolveStaticRelativePath(RouteDefinition route, String requestPath) {
        if (route.upstreamPath() != null && !route.upstreamPath().isBlank()) {
            return route.upstreamPath().trim();
        }
        String prefix = route.pathPrefix() == null ? "" : route.pathPrefix();
        if (requestPath == null || requestPath.isBlank()) {
            return "";
        }
        if (prefix.isBlank()) {
            return requestPath;
        }
        if (!requestPath.startsWith(prefix)) {
            return requestPath;
        }
        String remaining = requestPath.substring(prefix.length());
        return remaining.isBlank() ? "" : remaining;
    }

    private String guessContentType(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html") || name.endsWith(".htm")) {
            return "text/html; charset=UTF-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        if (name.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }
        if (name.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        }
        if (name.endsWith(".txt")) {
            return "text/plain; charset=UTF-8";
        }
        if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private FilterExecutionPlan compilePlan(RouteDefinition route) {
        FilterExecutionPlan compiled = filterPlanCompiler.compile(
                route.id(),
                route.pluginBindings() == null ? java.util.List.of() : route.pluginBindings(),
                route.policySet() == null ? Map.of() : route.policySet().entries()
        );
        var invocations = new java.util.ArrayList<>(compiled.invocations());
        invocations.add(new io.homeey.gateway.plugin.api.FilterInvocation(
                "bootstrap-routing",
                routingGatewayFilter,
                io.homeey.gateway.plugin.api.FilterFailPolicy.FAIL_CLOSE
        ));
        return new FilterExecutionPlan(invocations);
    }

    private HttpResponseMessage textResponse(int status, String text) {
        return new HttpResponseMessage(
                status,
                Map.of("content-type", "text/plain; charset=UTF-8"),
                text.getBytes(StandardCharsets.UTF_8)
        );
    }

    private HttpResponseMessage withTraceHeader(HttpResponseMessage response, String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return response;
        }
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        if (response.headers() != null) {
            headers.putAll(response.headers());
        }
        headers.put("X-Trace-Id", traceId);
        return new HttpResponseMessage(response.statusCode(), Map.copyOf(headers), response.body());
    }
}
