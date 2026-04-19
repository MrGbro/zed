package io.homeey.gateway.transport.reactor;

import io.homeey.gateway.transport.api.GatewayRequestHandler;
import io.homeey.gateway.transport.api.TransportServer;
import io.homeey.gateway.transport.api.TransportServerFactory;

public final class ReactorTransportServerFactory implements TransportServerFactory {
    @Override
    public TransportServer create(int port, GatewayRequestHandler requestHandler) {
        return new ReactorTransportServer(port, requestHandler);
    }
}
