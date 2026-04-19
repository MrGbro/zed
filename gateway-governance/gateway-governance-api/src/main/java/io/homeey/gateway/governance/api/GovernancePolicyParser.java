package io.homeey.gateway.governance.api;

import io.homeey.gateway.common.spi.SPI;

import java.util.Map;

@SPI("noop")
public interface GovernancePolicyParser {
    GovernancePolicy parse(Map<String, Object> entries);
}
