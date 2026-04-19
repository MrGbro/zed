package io.homeey.gateway.transport.reactor;

import io.homeey.gateway.transport.api.GatewayRequestHandler;
import io.homeey.gateway.transport.api.HttpRequestMessage;
import io.homeey.gateway.transport.api.HttpResponseMessage;
import io.homeey.gateway.transport.api.TransportServer;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpServer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 基于Reactor Netty的传输服务器实现。
 * <p>
 * 该实现使用Reactor Netty作为HTTP服务器，支持HTTP/1.1和HTTP/2，提供响应式的请求处理能力。
 * 与 {@link io.homeey.gateway.transport.netty.NettyTransportServer} 类似，但直接使用
 * Reactor Netty的高级API，代码更加简洁。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public final class ReactorTransportServer implements TransportServer {
    private final int port;
    private final GatewayRequestHandler requestHandler;
    private volatile DisposableServer server;

    /**
     * 构造传输服务器，使用默认请求处理器。
     *
     * @param port 监听端口
     */
    public ReactorTransportServer(int port) {
        this(port, ReactorTransportServer::defaultHandler);
    }

    /**
     * 构造传输服务器，使用自定义请求处理器。
     *
     * @param port           监听端口
     * @param requestHandler 请求处理器，负责处理HTTP请求
     */
    public ReactorTransportServer(int port, GatewayRequestHandler requestHandler) {
        this.port = port;
        this.requestHandler = requestHandler;
    }

    /**
     * 启动HTTP服务器，开始监听指定端口。
     * <p>
     * 该方法创建一个Reactor Netty HTTP服务器，配置如下：
     * <ul>
     *   <li>监听所有网络接口（0.0.0.0）</li>
     *   <li>支持HTTP/1.1和HTTP/2明文协议（H2C）</li>
     *   <li>对每个请求：解析HTTP消息、调用请求处理器、返回响应</li>
     *   <li>自动确保路径以斜杠开头，保持路由一致性</li>
     * </ul>
     * </p>
     *
     * @return 启动完成的CompletionStage
     */
    @Override
    public CompletionStage<Void> start() {
        return HttpServer.create()
                .host("0.0.0.0")
                .port(port)
                .protocol(HttpProtocol.HTTP11, HttpProtocol.H2C)
                .handle((request, response) -> request.receive().aggregate().asByteArray()
                        .defaultIfEmpty(new byte[0])
                        .flatMap(body -> {
                            Map<String, String> headers = new LinkedHashMap<>();
                            request.requestHeaders().forEach(entry -> headers.put(entry.getKey(), entry.getValue()));
                            String query = request.uri().contains("?")
                                    ? request.uri().substring(request.uri().indexOf('?') + 1)
                                    : "";
                            String host = request.requestHeaders().get(HttpHeaderNames.HOST);
                            String path = request.path();
                            if (!path.isEmpty() && !path.startsWith("/")) {
                                path = "/" + path;
                            }
                            HttpRequestMessage message = new HttpRequestMessage(
                                    request.method().name(),
                                    host == null ? "" : host,
                                    path,
                                    query,
                                    headers,
                                    body
                            );
                            return Mono.fromCompletionStage(requestHandler.handle(message))
                                    .flatMap(handlerResponse -> {
                                        response.status(HttpResponseStatus.valueOf(handlerResponse.statusCode()));
                                        if (handlerResponse.headers() != null) {
                                            handlerResponse.headers().forEach(response::header);
                                        }
                                        byte[] responseBody = handlerResponse.body() == null
                                                ? new byte[0]
                                                : handlerResponse.body();
                                        return response.sendByteArray(Mono.just(responseBody)).then();
                                    })
                                    .onErrorResume(throwable -> {
                                        response.status(HttpResponseStatus.INTERNAL_SERVER_ERROR);
                                        return response.sendString(Mono.just("internal error")).then();
                                    });
                        }))
                .bind()
                .doOnNext(bound -> this.server = bound)
                .then()
                .toFuture();
    }

    /**
     * 停止HTTP服务器，释放资源。
     * <p>
     * 该方法优雅地关闭服务器，等待最多5秒让现有请求处理完成。
     * </p>
     *
     * @return 停止完成的CompletionStage
     */
    @Override
    public CompletionStage<Void> stop() {
        return CompletableFuture.runAsync(() -> {
            DisposableServer current = server;
            if (current != null) {
                current.disposeNow(Duration.ofSeconds(5));
            }
        });
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
