package io.homeey.gateway.transport.netty;

import io.homeey.gateway.transport.api.HttpRequestMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.util.LinkedHashMap;
import java.util.Map;

public record NettyRequestAdapter(FullHttpRequest request) {
    public HttpRequestMessage toMessage() {
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        Map<String, String> headers = new LinkedHashMap<>();
        request.headers().forEach(entry -> headers.put(entry.getKey(), entry.getValue()));
        String host = request.headers().get(HttpHeaderNames.HOST, "");
        byte[] body = new byte[request.content().readableBytes()];
        request.content().getBytes(request.content().readerIndex(), body);
        String query = request.uri().contains("?")
                ? request.uri().substring(request.uri().indexOf('?') + 1)
                : "";
        return new HttpRequestMessage(
                request.method().name(),
                host,
                decoder.path(),
                query,
                headers,
                body
        );
    }
}
