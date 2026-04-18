package io.homeey.gateway.transport.api;

import java.util.concurrent.CompletionStage;

public interface TransportServer {
    CompletionStage<Void> start();

    CompletionStage<Void> stop();
}