package io.homeey.gateway.governance.api;

import io.homeey.gateway.common.spi.SPI;
import io.homeey.gateway.transport.api.HttpResponseMessage;

@SPI("local")
public interface CircuitBreakerPolicyHandler extends PolicyHandler<CircuitBreakerPolicy> {
    boolean allow(GovernanceExecutionContext context, CircuitBreakerPolicy policy, GovernanceStateStore stateStore);

    void record(
            GovernanceExecutionContext context,
            CircuitBreakerPolicy policy,
            GovernanceStateStore stateStore,
            HttpResponseMessage response,
            Throwable throwable
    );

    @Override
    default String ability() {
        return CircuitBreakerPolicy.ABILITY;
    }
}
