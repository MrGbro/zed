package io.homeey.gateway.bootstrap.wiring;

import io.homeey.gateway.core.route.RouteDefinition;
import io.homeey.gateway.core.route.RouteTableSnapshot;
import io.homeey.gateway.core.runtime.RuntimeSnapshotManager;
import io.homeey.gateway.plugin.api.PolicySet;
import io.homeey.gateway.proxy.api.ProxyClient;
import io.homeey.gateway.proxy.api.ProxyRequest;
import io.homeey.gateway.proxy.api.ProxyResponse;
import io.homeey.gateway.registry.api.ServiceDiscoveryProvider;
import io.homeey.gateway.transport.api.HttpRequestMessage;
import io.homeey.gateway.transport.api.HttpResponseMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultGatewayRequestHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldServeStaticFileWhenRouteMatches() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello-static", StandardCharsets.UTF_8);
        DefaultGatewayRequestHandler handler = newHandler(
                "r-static",
                "/assets",
                "GET",
                "static",
                "/hello.txt"
        );
        HttpRequestMessage request = new HttpRequestMessage(
                "GET",
                "api.example.com",
                "/assets/hello.txt",
                "",
                Map.of(),
                new byte[0]
        );

        HttpResponseMessage response = handler.handle(request).toCompletableFuture().join();

        assertEquals(200, response.statusCode());
        assertEquals("text/plain; charset=UTF-8", response.headers().get("content-type"));
        assertEquals("hello-static", new String(response.body(), StandardCharsets.UTF_8));
    }

    @Test
    void shouldReturn404WhenStaticFileMissing() {
        DefaultGatewayRequestHandler handler = newHandler(
                "r-static",
                "/assets",
                "GET",
                "static",
                "/missing.txt"
        );
        HttpRequestMessage request = new HttpRequestMessage(
                "GET",
                "api.example.com",
                "/assets/missing.txt",
                "",
                Map.of(),
                new byte[0]
        );

        HttpResponseMessage response = handler.handle(request).toCompletableFuture().join();

        assertEquals(404, response.statusCode());
    }

    @Test
    void shouldReturn405WhenStaticMethodNotAllowed() {
        DefaultGatewayRequestHandler handler = newHandler(
                "r-static",
                "/assets",
                "POST",
                "static",
                "/hello.txt"
        );
        HttpRequestMessage request = new HttpRequestMessage(
                "POST",
                "api.example.com",
                "/assets/hello.txt",
                "",
                Map.of(),
                "body".getBytes(StandardCharsets.UTF_8)
        );

        HttpResponseMessage response = handler.handle(request).toCompletableFuture().join();

        assertEquals(405, response.statusCode());
    }

    @Test
    void shouldRejectPathTraversalForStaticRoute() {
        DefaultGatewayRequestHandler handler = newHandler(
                "r-static",
                "/assets",
                "GET",
                "static",
                "/../secret.txt"
        );
        HttpRequestMessage request = new HttpRequestMessage(
                "GET",
                "api.example.com",
                "/assets/../secret.txt",
                "",
                Map.of(),
                new byte[0]
        );

        HttpResponseMessage response = handler.handle(request).toCompletableFuture().join();

        assertEquals(403, response.statusCode());
    }

    @Test
    void shouldExposeMetricsFromObserveProvider() {
        RuntimeSnapshotManager snapshotManager = new RuntimeSnapshotManager(Map.of("version", "v0"));
        ServiceDiscoveryProvider discoveryProvider = new ServiceDiscoveryProvider() {
            @Override
            public CompletionStage<List<String>> getInstances(String serviceName) {
                return CompletableFuture.completedFuture(List.of());
            }

            @Override
            public CompletionStage<Void> register(String serviceName, String endpoint) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> subscribe(String serviceName, java.util.function.Consumer<List<String>> listener) {
                return CompletableFuture.completedFuture(null);
            }
        };
        ProxyClient proxyClient = new ProxyClient() {
            @Override
            public CompletionStage<ProxyResponse> execute(ProxyRequest request) {
                return CompletableFuture.completedFuture(new ProxyResponse(200, Map.of(), new byte[0]));
            }

            @Override
            public void close() {
                // no-op
            }
        };
        io.homeey.gateway.observe.api.ObserveProvider provider = new io.homeey.gateway.observe.api.ObserveProvider() {
            @Override
            public String metricsSnapshot() {
                return "gateway_requests_total 1\n";
            }
        };
        DefaultGatewayRequestHandler handler = new DefaultGatewayRequestHandler(
                snapshotManager,
                discoveryProvider,
                proxyClient,
                tempDir.toString(),
                provider,
                "/metrics"
        );
        HttpResponseMessage response = handler.handle(new HttpRequestMessage(
                "GET",
                "api.example.com",
                "/metrics",
                "",
                Map.of(),
                new byte[0]
        )).toCompletableFuture().join();
        assertEquals(200, response.statusCode());
        assertEquals("text/plain; version=0.0.4; charset=UTF-8", response.headers().get("content-type"));
        assertTrue(new String(response.body(), StandardCharsets.UTF_8).contains("gateway_requests_total"));
    }

    @Test
    void shouldPutRoutePolicySetIntoContextAttributes() {
        RuntimeSnapshotManager snapshotManager = new RuntimeSnapshotManager(Map.of("version", "v0"));
        RouteDefinition route = new RouteDefinition(
                "r1",
                "api.example.com",
                "/orders",
                "GET",
                Map.of(),
                "upstream-a",
                "/orders",
                List.of(),
                new PolicySet(Map.of("governance.enabled", true, "governance.ratelimit.enabled", true, "governance.ratelimit.qps", 1))
        );
        snapshotManager.onRouteSnapshotPublished(new RouteTableSnapshot("v1", List.of(route)));

        ServiceDiscoveryProvider discoveryProvider = new ServiceDiscoveryProvider() {
            @Override
            public CompletionStage<List<String>> getInstances(String serviceName) {
                return CompletableFuture.completedFuture(List.of("127.0.0.1:19000"));
            }

            @Override
            public CompletionStage<Void> register(String serviceName, String endpoint) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> subscribe(String serviceName, java.util.function.Consumer<List<String>> listener) {
                return CompletableFuture.completedFuture(null);
            }
        };

        ProxyClient proxyClient = new ProxyClient() {
            @Override
            public CompletionStage<ProxyResponse> execute(ProxyRequest request) {
                return CompletableFuture.completedFuture(new ProxyResponse(200, Map.of("content-type", "text/plain"), "ok".getBytes(StandardCharsets.UTF_8)));
            }

            @Override
            public void close() {
                // no-op
            }
        };

        DefaultGatewayRequestHandler handler = new DefaultGatewayRequestHandler(snapshotManager, discoveryProvider, proxyClient, tempDir.toString());

        HttpResponseMessage response = handler.handle(new HttpRequestMessage(
                "GET",
                "api.example.com",
                "/orders",
                "",
                Map.of(),
                new byte[0]
        )).toCompletableFuture().join();

        assertEquals(200, response.statusCode());
        assertTrue(route.policySet().entries().containsKey("governance.enabled"));
        assertEquals(true, route.policySet().entries().get("governance.enabled"));
    }

    private DefaultGatewayRequestHandler newHandler(
            String routeId,
            String pathPrefix,
            String method,
            String upstreamService,
            String upstreamPath
    ) {
        RuntimeSnapshotManager snapshotManager = new RuntimeSnapshotManager(Map.of("version", "v0"));
        RouteDefinition route = new RouteDefinition(
                routeId,
                "api.example.com",
                pathPrefix,
                method,
                Map.of(),
                upstreamService,
                upstreamPath,
                List.of(),
                new PolicySet(Map.of())
        );
        snapshotManager.onRouteSnapshotPublished(new RouteTableSnapshot("v1", List.of(route)));

        ServiceDiscoveryProvider discoveryProvider = new ServiceDiscoveryProvider() {
            @Override
            public CompletionStage<List<String>> getInstances(String serviceName) {
                return CompletableFuture.completedFuture(List.of());
            }

            @Override
            public CompletionStage<Void> register(String serviceName, String endpoint) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> subscribe(String serviceName, java.util.function.Consumer<List<String>> listener) {
                return CompletableFuture.completedFuture(null);
            }
        };

        ProxyClient proxyClient = new ProxyClient() {
            @Override
            public CompletionStage<ProxyResponse> execute(ProxyRequest request) {
                return CompletableFuture.failedFuture(new IllegalStateException("proxy should not be called for static"));
            }

            @Override
            public void close() {
                // no-op
            }
        };

        DefaultGatewayRequestHandler handler = new DefaultGatewayRequestHandler(snapshotManager, discoveryProvider, proxyClient, tempDir.toString());
        assertTrue(handler != null);
        return handler;
    }
}
