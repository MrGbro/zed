package io.homeey.gateway.governance.api;

import io.homeey.gateway.common.spi.SPI;
import io.homeey.gateway.transport.api.HttpResponseMessage;

@SPI("local")
public interface DegradePolicyHandler extends PolicyHandler<DegradePolicy> {
    HttpResponseMessage degrade(
            GovernanceExecutionContext context,
            DegradePolicy policy,
            GovernanceFailureKind kind,
            Throwable cause
    );

    @Override
    default String ability() {
        return DegradePolicy.ABILITY;
    }
}
