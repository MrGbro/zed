package io.homeey.gateway.transport.vertx;

import io.homeey.gateway.transport.api.GatewayRequestHandler;
import io.homeey.gateway.transport.api.TransportServer;
import io.homeey.gateway.transport.api.TransportServerFactory;

/**
 * Eclipse Vert.x传输服务器工厂，用于创建 {@link VertxTransportServer} 实例。
 * <p>
 * 该工厂实现了 {@link TransportServerFactory} 接口，通过SPI机制支持
 * Vert.x作为网关的传输层实现。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public final class VertxTransportServerFactory implements TransportServerFactory {
    /**
     * 创建Vert.x传输服务器实例。
     *
     * @param port           监听端口
     * @param requestHandler 请求处理器，负责处理HTTP请求
     * @return VertxTransportServer实例
     */
    @Override
    public TransportServer create(int port, GatewayRequestHandler requestHandler) {
        return new VertxTransportServer(port, requestHandler);
    }
}
