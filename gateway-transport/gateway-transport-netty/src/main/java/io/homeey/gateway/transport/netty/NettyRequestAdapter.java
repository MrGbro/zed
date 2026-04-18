package io.homeey.gateway.transport.netty;

import io.netty.handler.codec.http.FullHttpRequest;

public record NettyRequestAdapter(FullHttpRequest request) {
    public String path() {
        return request.uri();
    }
}
