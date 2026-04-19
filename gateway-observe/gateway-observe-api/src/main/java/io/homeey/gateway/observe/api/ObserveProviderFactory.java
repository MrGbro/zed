package io.homeey.gateway.observe.api;

import io.homeey.gateway.common.spi.SPI;

/**
 * SPI factory for observe providers.
 */
@SPI("otel")
public interface ObserveProviderFactory {
    ObserveProvider create(ObserveOptions options);
}
