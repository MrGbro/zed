package io.homeey.gateway.proxy.reactor;

import io.homeey.gateway.proxy.api.ProxyClient;
import io.homeey.gateway.proxy.api.ProxyRequest;
import io.homeey.gateway.proxy.api.ProxyResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public final class ReactorNettyProxyClient implements ProxyClient {
    private final HttpClient client;

    public ReactorNettyProxyClient() {
        this(HttpClient.create());
    }

    public ReactorNettyProxyClient(HttpClient client) {
        this.client = client;
    }

    @Override
    public CompletionStage<ProxyResponse> execute(ProxyRequest request) {
        return client
                .headers(headers -> {
                    if (request.headers() != null) {
                        request.headers().forEach(headers::set);
                    }
                })
                .request(HttpMethod.valueOf(request.method()))
                .uri(request.url())
                .send((r, out) -> {
                    byte[] body = request.body() == null ? new byte[0] : request.body();
                    ByteBuf buffer = Unpooled.wrappedBuffer(body);
                    return out.send(Mono.just(buffer));
                })
                .responseSingle((response, content) -> content.asByteArray().map(bytes -> {
                    Map<String, String> headers = new LinkedHashMap<>();
                    response.responseHeaders().forEach(entry -> headers.put(entry.getKey(), entry.getValue()));
                    return new ProxyResponse(response.status().code(), headers, bytes);
                }))
                .toFuture();
    }

    @Override
    public void close() {
        // Reactor HttpClient has no explicit close for shared resources in this baseline.
    }
}
