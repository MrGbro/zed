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

/**
 * 基于Eclipse Vert.x的传输服务器实现。
 * <p>
 * 该实现使用Vert.x作为HTTP服务器，提供高性能、事件驱动的请求处理能力。
 * Vert.x以其轻量级和高并发特性著称，适合构建高吞吐量的网关服务。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public final class VertxTransportServer implements TransportServer {
    private static final String HOST_HEADER = "Host";
    private final int port;
    private final GatewayRequestHandler requestHandler;
    private final Vertx vertx;
    private volatile HttpServer server;

    /**
     * 构造传输服务器，使用默认请求处理器。
     *
     * @param port 监听端口
     */
    public VertxTransportServer(int port) {
        this(port, VertxTransportServer::defaultHandler);
    }

    /**
     * 构造传输服务器，使用自定义请求处理器。
     *
     * @param port           监听端口
     * @param requestHandler 请求处理器，负责处理HTTP请求
     */
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

    /**
     * 将网关响应应用到Vert.x路由上下文。
     * <p>
     * 该方法设置响应状态码、响应头，并发送响应体。
     * </p>
     *
     * @param ctx      Vert.x路由上下文
     * @param response 网关统一的HTTP响应消息
     */
    private static void applyResponse(io.vertx.ext.web.RoutingContext ctx, HttpResponseMessage response) {
        ctx.response().setStatusCode(response.statusCode());
        if (response.headers() != null) {
            response.headers().forEach((k, v) -> ctx.response().putHeader(k, v));
        }
        byte[] bytes = response.body() == null ? new byte[0] : response.body();
        ctx.response().end(io.vertx.core.buffer.Buffer.buffer(bytes));
    }

    /**
     * 写入内部错误响应。
     * <p>
     * 该方法返回HTTP 500状态码和简单的错误消息。
     * 如果响应已经结束，则不执行任何操作。
     * </p>
     *
     * @param ctx Vert.x路由上下文
     */
    private static void writeInternalError(io.vertx.ext.web.RoutingContext ctx) {
        if (ctx.response().ended()) {
            return;
        }
        ctx.response()
                .setStatusCode(500)
                .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
                .end("internal error");
    }

    /**
     * 默认请求处理器，提供基本的健康检查和404响应。
     * <p>
     * 该处理器支持：
     * <ul>
     *   <li>/ping 或包含 "ping" 的路径：返回 200 OK 和 "ok" 文本</li>
     *   <li>其他路径：返回 404 Not Found 和 "not found" 文本</li>
     * </ul>
     * </p>
     *
     * @param request HTTP请求消息
     * @return HTTP响应消息的CompletionStage
     */
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
