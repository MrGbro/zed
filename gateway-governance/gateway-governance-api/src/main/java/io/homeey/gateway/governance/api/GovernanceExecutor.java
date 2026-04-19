package io.homeey.gateway.governance.api;

import io.homeey.gateway.common.spi.SPI;
import io.homeey.gateway.plugin.api.GatewayContext;
import io.homeey.gateway.proxy.api.ProxyResponse;
import io.homeey.gateway.transport.api.HttpResponseMessage;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

@SPI("noop")
public interface GovernanceExecutor {
    CompletionStage<HttpResponseMessage> execute(
            GatewayContext context,
            GovernancePolicy policy,
            Supplier<CompletionStage<ProxyResponse>> upstreamCall
    );
}
