package io.homeey.gateway.transport.netty;

import io.homeey.gateway.transport.api.HttpResponseMessage;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;

public final class NettyResponseAdapter {

    private NettyResponseAdapter() {
    }

    public static FullHttpResponse okText(String body) {
        byte[] bytes = body.getBytes(CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(bytes)
        );
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().setInt(CONTENT_LENGTH, bytes.length);
        return response;
    }

    public static FullHttpResponse fromMessage(HttpResponseMessage message) {
        byte[] bytes = message.body() == null ? new byte[0] : message.body();
        HttpResponseStatus status = HttpResponseStatus.valueOf(message.statusCode());
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(bytes)
        );
        if (message.headers() != null) {
            message.headers().forEach(response.headers()::set);
        }
        if (!response.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
            response.headers().setInt(CONTENT_LENGTH, bytes.length);
        }
        return response;
    }
}
