package io.homeey.gateway.transport.api;

import java.util.concurrent.CompletionStage;

/**
 * 网关请求处理器接口，用于处理传入的HTTP请求。
 * <p>
 * 该接口是网关的核心处理入口，负责接收HTTP请求并返回响应。
 * 实现类应该完成路由匹配、过滤器链执行、代理转发等核心逻辑。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
@FunctionalInterface
public interface GatewayRequestHandler {
    /**
     * 处理HTTP请求。
     *
     * @param request HTTP请求消息
     * @return HTTP响应消息的异步结果
     */
    CompletionStage<HttpResponseMessage> handle(HttpRequestMessage request);
}
