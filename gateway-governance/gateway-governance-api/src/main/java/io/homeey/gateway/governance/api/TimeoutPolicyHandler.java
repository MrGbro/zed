package io.homeey.gateway.governance.api;

import io.homeey.gateway.common.spi.SPI;
import io.homeey.gateway.transport.api.HttpResponseMessage;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

@SPI("local")
public interface TimeoutPolicyHandler extends PolicyHandler<TimeoutPolicy> {
    CompletionStage<HttpResponseMessage> execute(
            GovernanceExecutionContext context,
            TimeoutPolicy policy,
            Supplier<CompletionStage<HttpResponseMessage>> attempt,
            GovernanceScheduler scheduler
    );

    @Override
    default String ability() {
        return TimeoutPolicy.ABILITY;
    }
}
