package io.homeey.gateway.transport.api;

import java.util.Map;

/**
 * HTTP响应消息记录，封装网关返回给客户端的响应信息。
 * <p>
 * 该记录包含HTTP状态码、响应头和响应体，用于构建HTTP响应并发送回客户端。
 * </p>
 *
 * @param statusCode HTTP状态码（如200、404、500）
 * @param headers    响应头映射
 * @param body       响应体字节数组
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public record HttpResponseMessage(
        int statusCode,
        Map<String, String> headers,
        byte[] body
) {
}
