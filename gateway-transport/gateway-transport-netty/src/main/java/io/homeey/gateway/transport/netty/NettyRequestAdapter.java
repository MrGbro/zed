package io.homeey.gateway.transport.netty;

import io.homeey.gateway.transport.api.HttpRequestMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Netty请求适配器，将Netty的FullHttpRequest转换为网关统一的HttpRequestMessage。
 * <p>
 * 该适配器负责从Netty HTTP请求中提取方法、路径、查询参数、请求头和请求体，
 * 并构建为网关内部使用的标准请求消息格式。
 * </p>
 *
 * @param request Netty的完整HTTP请求对象
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public record NettyRequestAdapter(FullHttpRequest request) {
    /**
     * 将Netty请求转换为网关统一的HTTP请求消息。
     * <p>
     * 该方法执行以下转换：
     * <ul>
     *   <li>提取HTTP方法名称</li>
     *   <li>从Host头提取主机名</li>
     *   <li>使用QueryStringDecoder解析路径和查询参数</li>
     *   <li>复制所有请求头到Map</li>
     *   <li>读取请求体字节数组</li>
     * </ul>
     * </p>
     *
     * @return 网关统一的HTTP请求消息
     */
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
