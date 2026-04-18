package io.homeey.gateway.transport.netty;

import io.homeey.gateway.transport.api.GatewayRequestHandler;
import io.homeey.gateway.transport.api.HttpRequestMessage;
import io.homeey.gateway.transport.api.HttpResponseMessage;
import io.homeey.gateway.transport.api.TransportServer;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpHeaderNames;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class NettyTransportServer implements TransportServer {
    private final int port;
    private final GatewayRequestHandler requestHandler;
    private volatile DisposableServer server;

    public NettyTransportServer(int port) {
        this(port, NettyTransportServer::defaultHandler);
    }

    public NettyTransportServer(int port, GatewayRequestHandler requestHandler) {
        this.port = port;
        this.requestHandler = requestHandler;
    }

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
                            HttpRequestMessage message = new HttpRequestMessage(
                                    request.method().name(),
                                    host == null ? "" : host,
                                    request.path(),
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

    @Override
    public CompletionStage<Void> stop() {
        return CompletableFuture.runAsync(() -> {
            DisposableServer current = server;
            if (current != null) {
                current.disposeNow(Duration.ofSeconds(5));
            }
        });
    }

    private static CompletionStage<HttpResponseMessage> defaultHandler(HttpRequestMessage request) {
        String path = request.path() == null ? "" : request.path().toLowerCase();
        if (path.startsWith("/ping") || path.contains("ping")) {
            return CompletableFuture.completedFuture(new HttpResponseMessage(
                    200,
                    Map.of("content-type", "text/plain; charset=UTF-8"),
                    "ok".getBytes()
            ));
        }
        return CompletableFuture.completedFuture(new HttpResponseMessage(
                404,
                Map.of("content-type", "text/plain; charset=UTF-8"),
                "not found".getBytes()
        ));
    }
}
