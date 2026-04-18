package io.homeey.gateway.proxy.api;

import io.homeey.gateway.common.spi.SPI;

@SPI("async-http-client")
public interface ProxyClientFactory {
    ProxyClient create();
}
