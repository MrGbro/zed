package io.homeey.gateway.transport.vertx;

import io.homeey.gateway.transport.api.GatewayRequestHandler;
import io.homeey.gateway.transport.api.TransportServer;
import io.homeey.gateway.transport.api.TransportServerFactory;

public final class VertxTransportServerFactory implements TransportServerFactory {
    @Override
    public TransportServer create(int port, GatewayRequestHandler requestHandler) {
        return new VertxTransportServer(port, requestHandler);
    }
}
