package io.homeey.gateway.proxy.api;

import java.util.Map;

/**
 * 代理响应记录，封装从上游服务接收的响应信息。
 * <p>
 * 该记录包含HTTP状态码、响应头和响应体，用于构建返回给客户端的HTTP响应。
 * </p>
 *
 * @param statusCode HTTP状态码（如200、404、500）
 * @param headers    响应头映射
 * @param body       响应体字节数组
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public record ProxyResponse(
        int statusCode,
        Map<String, String> headers,
        byte[] body
) {
}
