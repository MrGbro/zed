package io.homeey.gateway.governance.api;

import io.homeey.gateway.plugin.api.GatewayContext;
import io.homeey.gateway.proxy.api.ProxyResponse;
import io.homeey.gateway.transport.api.HttpResponseMessage;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class NoopGovernanceExecutor implements GovernanceExecutor {
    @Override
    public CompletionStage<HttpResponseMessage> execute(
            GatewayContext context,
            GovernancePolicy policy,
            Supplier<CompletionStage<ProxyResponse>> upstreamCall
    ) {
        return upstreamCall.get().thenApply(resp -> new HttpResponseMessage(
                resp.statusCode(),
                resp.headers() == null ? Map.of() : resp.headers(),
                resp.body() == null ? new byte[0] : resp.body()
        ));
    }
}
