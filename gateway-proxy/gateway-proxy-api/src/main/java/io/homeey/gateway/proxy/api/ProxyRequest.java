package io.homeey.gateway.proxy.api;

import java.util.Map;

/**
 * 代理请求记录，封装转发到上游服务的请求信息。
 * <p>
 * 该记录包含HTTP方法、URL、请求头和请求体，用于构建向上游服务发送的HTTP请求。
 * </p>
 *
 * @param method  HTTP方法（如GET、POST）
 * @param url     目标URL
 * @param headers 请求头映射
 * @param body    请求体字节数组
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public record ProxyRequest(
        String method,
        String url,
        Map<String, String> headers,
        byte[] body
) {
}
