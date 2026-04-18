package io.homeey.gateway.registry.api;

import io.homeey.gateway.common.spi.SPI;

@SPI("nacos")
public interface ServiceDiscoveryProviderFactory {
    ServiceDiscoveryProvider create(String serverAddr);
}
