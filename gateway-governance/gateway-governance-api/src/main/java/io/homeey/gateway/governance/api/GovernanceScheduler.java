package io.homeey.gateway.governance.api;

import io.homeey.gateway.common.spi.SPI;

import java.util.concurrent.CompletionStage;

@SPI("local")
public interface GovernanceScheduler {
    <T> CompletionStage<T> withTimeout(CompletionStage<T> origin, long timeoutMillis, String message);

    CompletionStage<Void> delay(long delayMillis);
}
