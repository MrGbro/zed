package io.homeey.gateway.transport.api;

import io.homeey.gateway.common.spi.SPI;

@SPI("netty")
public interface TransportServerFactory {
    TransportServer create(int port, GatewayRequestHandler requestHandler);
}
