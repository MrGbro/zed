package io.homeey.gateway.transport.netty;

import io.homeey.gateway.transport.api.GatewayRequestHandler;
import io.homeey.gateway.transport.api.TransportServer;
import io.homeey.gateway.transport.api.TransportServerFactory;

/**
 * Netty传输服务器工厂，用于创建 {@link NettyTransportServer} 实例。
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public final class NettyTransportServerFactory implements TransportServerFactory {
    @Override
    public TransportServer create(int port, GatewayRequestHandler requestHandler) {
        return new NettyTransportServer(port, requestHandler);
    }
}
