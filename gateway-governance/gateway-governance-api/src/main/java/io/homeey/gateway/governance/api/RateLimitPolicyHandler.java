package io.homeey.gateway.governance.api;

import io.homeey.gateway.common.spi.SPI;
import io.homeey.gateway.transport.api.HttpResponseMessage;

@SPI("local")
public interface RateLimitPolicyHandler extends PolicyHandler<RateLimitPolicy> {
    boolean allow(GovernanceExecutionContext context, RateLimitPolicy policy, GovernanceStateStore stateStore);

    @Override
    default String ability() {
        return RateLimitPolicy.ABILITY;
    }
}
