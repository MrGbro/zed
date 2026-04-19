package io.homeey.gateway.transport.vertx;

import io.homeey.gateway.transport.api.GatewayRequestHandler;
import io.homeey.gateway.transport.api.HttpRequestMessage;
import io.homeey.gateway.transport.api.HttpResponseMessage;
import io.homeey.gateway.transport.api.TransportServer;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class VertxTransportServer implements TransportServer {
    private static final String HOST_HEADER = "Host";
    private final int port;
    private final GatewayRequestHandler requestHandler;
    private final Vertx vertx;
    private volatile HttpServer server;

    public VertxTransportServer(int port) {
        this(port, VertxTransportServer::defaultHandler);
    }

    public VertxTransportServer(int port, GatewayRequestHandler requestHandler) {
        this.port = port;
        this.requestHandler = requestHandler;
        this.vertx = Vertx.vertx();
    }

    @Override
    public CompletionStage<Void> start() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        HttpServerOptions options = new HttpServerOptions()
                .setHost("0.0.0.0")
                .setPort(port);
        HttpServer httpServer = vertx.createHttpServer(options);
        Router router = Router.router(vertx);
        router.route().handler(ctx ->
                ctx.request().body().onComplete(bodyResult -> {
                    if (bodyResult.failed()) {
                        writeInternalError(ctx);
                        return;
                    }
                    Map<String, String> headers = new LinkedHashMap<>();
                    ctx.request().headers().forEach(entry -> headers.put(entry.getKey(), entry.getValue()));
                    String host = headers.getOrDefault(HttpHeaders.HOST.toString(), "");
                    if (host == null || host.isEmpty()) {
                        host = headers.getOrDefault(HOST_HEADER, "");
                    }
                    String path = ctx.normalizedPath();
                    if (path != null && !path.isEmpty() && !path.startsWith("/")) {
                        path = "/" + path;
                    }
                    String query = ctx.request().query() == null ? "" : ctx.request().query();
                    byte[] body = bodyResult.result() == null ? new byte[0] : bodyResult.result().getBytes();
                    HttpRequestMessage message = new HttpRequestMessage(
                            ctx.request().method().name(),
                            host,
                            path,
                            query,
                            headers,
                            body
                    );
                    requestHandler.handle(message).whenComplete((response, throwable) -> {
                        if (throwable != null) {
                            writeInternalError(ctx);
                            return;
                        }
                        applyResponse(ctx, response);
                    });
                }));
        httpServer.requestHandler(router).listen(ar -> {
            if (ar.succeeded()) {
                this.server = httpServer;
                future.complete(null);
                return;
            }
            future.completeExceptionally(ar.cause());
        });
        return future;
    }

    @Override
    public CompletionStage<Void> stop() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        HttpServer current = this.server;
        if (current == null) {
            vertx.close(ar -> {
                if (ar.succeeded()) {
                    future.complete(null);
                } else {
                    future.completeExceptionally(ar.cause());
                }
            });
            return future;
        }
        current.close(ar -> vertx.close(closeAr -> {
            if (ar.failed()) {
                future.completeExceptionally(ar.cause());
                return;
            }
            if (closeAr.failed()) {
                future.completeExceptionally(closeAr.cause());
                return;
            }
            future.complete(null);
        }));
        return future;
    }

    private static void applyResponse(io.vertx.ext.web.RoutingContext ctx, HttpResponseMessage response) {
        ctx.response().setStatusCode(response.statusCode());
        if (response.headers() != null) {
            response.headers().forEach((k, v) -> ctx.response().putHeader(k, v));
        }
        byte[] bytes = response.body() == null ? new byte[0] : response.body();
        ctx.response().end(io.vertx.core.buffer.Buffer.buffer(bytes));
    }

    private static void writeInternalError(io.vertx.ext.web.RoutingContext ctx) {
        if (ctx.response().ended()) {
            return;
        }
        ctx.response()
                .setStatusCode(500)
                .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
                .end("internal error");
    }

    private static CompletionStage<HttpResponseMessage> defaultHandler(HttpRequestMessage request) {
        String path = request.path() == null ? "" : request.path().toLowerCase();
        if (path.startsWith("/ping") || path.contains("ping")) {
            return CompletableFuture.completedFuture(new HttpResponseMessage(
                    200,
                    Map.of("content-type", "text/plain; charset=UTF-8"),
                    "ok".getBytes(StandardCharsets.UTF_8)
            ));
        }
        return CompletableFuture.completedFuture(new HttpResponseMessage(
                404,
                Map.of("content-type", "text/plain; charset=UTF-8"),
                "not found".getBytes(StandardCharsets.UTF_8)
        ));
    }
}
