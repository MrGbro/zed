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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final FilterExecutionPlanCompiler filterPlanCompiler;
    private final Path staticResourcesRoot;
    private final AtomicInteger rr = new AtomicInteger(0);

    public DefaultGatewayRequestHandler(
            RuntimeSnapshotManager snapshotManager,
            ServiceDiscoveryProvider discoveryProvider,
            ProxyClient proxyClient
    ) {
        this(snapshotManager, discoveryProvider, proxyClient, "static");
    }

    public DefaultGatewayRequestHandler(
            RuntimeSnapshotManager snapshotManager,
            ServiceDiscoveryProvider discoveryProvider,
            ProxyClient proxyClient,
            String staticResourcesDir
    ) {
        this.snapshotManager = snapshotManager;
        this.discoveryProvider = discoveryProvider;
        this.proxyClient = proxyClient;
        this.filterPlanCompiler = new FilterExecutionPlanCompiler();
        this.staticResourcesRoot = Path.of(staticResourcesDir == null || staticResourcesDir.isBlank() ? "static" : staticResourcesDir)
                .toAbsolutePath()
                .normalize();
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
        if ("static".equalsIgnoreCase(route.upstreamService())) {
            return CompletableFuture.completedFuture(serveStatic(route, request));
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
