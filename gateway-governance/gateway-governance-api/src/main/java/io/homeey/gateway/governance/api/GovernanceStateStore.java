package io.homeey.gateway.governance.api;

import io.homeey.gateway.common.spi.SPI;

import java.util.function.Supplier;

@SPI("local")
public interface GovernanceStateStore {
    <T> T computeIfAbsent(String key, Supplier<T> supplier, Class<T> type);
}
