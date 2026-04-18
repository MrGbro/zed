package io.homeey.gateway.config.api;

import io.homeey.gateway.common.spi.SPI;

@SPI("nacos")
public interface ConfigProviderFactory {
    ConfigProvider create(String serverAddr);
}
