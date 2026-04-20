package io.homeey.gateway.governance.api;

import io.homeey.gateway.common.spi.SPI;

import java.util.Map;

@SPI
public interface PolicyFactory<T extends GovernancePolicy> {
    String ability();

    T create(Map<String, Object> entries);
}
