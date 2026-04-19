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

/**
 * Netty响应适配器，提供将网关响应转换为Netty响应的工具方法。
 * <p>
 * 该类包含静态工厂方法，用于创建Netty的FullHttpResponse对象，
 * 支持从网关统一的HttpResponseMessage转换或快速创建常见响应。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public final class NettyResponseAdapter {

    /**
     * 私有构造函数，防止实例化。
     */
    private NettyResponseAdapter() {
    }

    /**
     * 创建HTTP 200 OK的文本响应。
     * <p>
     * 该方法创建一个状态码为200、Content-Type为text/plain的响应，
     * 使用UTF-8编码。
     * </p>
     *
     * @param body 响应体文本内容
     * @return Netty完整HTTP响应对象
     */
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

    /**
     * 从网关统一的HTTP响应消息创建Netty响应。
     * <p>
     * 该方法将HttpResponseMessage转换为Netty的FullHttpResponse，
     * 包括状态码、响应头和响应体的转换。如果响应头中未包含Content-Length，
     * 则自动根据响应体长度设置。
     * </p>
     *
     * @param message 网关统一的HTTP响应消息
     * @return Netty完整HTTP响应对象
     */
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
