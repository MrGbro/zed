package io.homeey.gateway.transport.api;

import java.util.Map;

/**
 * HTTP请求消息记录，封装网关接收的客户端请求信息。
 * <p>
 * 该记录包含HTTP方法、主机名、路径、查询参数、请求头和请求体，
 * 用于在网关内部传递和处理请求。
 * </p>
 *
 * @param method  HTTP方法（如GET、POST、PUT、DELETE）
 * @param host    主机名（从Host头提取，不含端口）
 * @param path    请求路径（以/开头）
 * @param query   查询参数字符串（不含?前缀）
 * @param headers 请求头映射
 * @param body    请求体字节数组
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public record HttpRequestMessage(
        String method,
        String host,
        String path,
        String query,
        Map<String, String> headers,
        byte[] body
) {
}
