package io.homeey.gateway.registry.api;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public interface ServiceDiscoveryProvider {
    CompletionStage<List<String>> getInstances(String serviceName);

    CompletionStage<Void> subscribe(String serviceName, Consumer<List<String>> listener);
}