package io.homeey.gateway.transport.api;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface GatewayRequestHandler {
    CompletionStage<HttpResponseMessage> handle(HttpRequestMessage request);
}
