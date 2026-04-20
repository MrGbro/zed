package io.homeey.gateway.governance.api;

import io.homeey.gateway.common.spi.SPI;

@SPI("local")
public interface FailureModeResolver {
    FailureMode resolve(String ability, FailureMode configured);
}
