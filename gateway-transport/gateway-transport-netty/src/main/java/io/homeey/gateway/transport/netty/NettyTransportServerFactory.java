package io.homeey.gateway.transport.netty;

import io.homeey.gateway.transport.api.GatewayRequestHandler;
import io.homeey.gateway.transport.api.TransportServer;
import io.homeey.gateway.transport.api.TransportServerFactory;

public final class NettyTransportServerFactory implements TransportServerFactory {
    @Override
    public TransportServer create(int port, GatewayRequestHandler requestHandler) {
        return new NettyTransportServer(port, requestHandler);
    }
}
