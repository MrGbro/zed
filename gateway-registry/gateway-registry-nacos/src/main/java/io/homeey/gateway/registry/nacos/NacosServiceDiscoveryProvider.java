package io.homeey.gateway.registry.nacos;

import io.homeey.gateway.registry.api.ServiceDiscoveryProvider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class NacosServiceDiscoveryProvider implements ServiceDiscoveryProvider {
    private final Map<String, List<String>> snapshots = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<List<String>>>> listeners = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<List<String>> getInstances(String serviceName) {
        return CompletableFuture.completedFuture(snapshots.getOrDefault(serviceName, List.of()));
    }

    @Override
    public CompletionStage<Void> subscribe(String serviceName, Consumer<List<String>> listener) {
        listeners.computeIfAbsent(serviceName, unused -> new CopyOnWriteArrayList<>())
                .add(listener);
        return CompletableFuture.completedFuture(null);
    }

    public void onNacosInstancesChanged(String serviceName, List<String> instances) {
        if (instances == null) {
            return;
        }
        List<String> immutable = List.copyOf(instances);
        snapshots.put(serviceName, immutable);
        for (Consumer<List<String>> listener : listeners.getOrDefault(serviceName, List.of())) {
            try {
                listener.accept(immutable);
            } catch (RuntimeException ignored) {
                // Keep current snapshot when callback fails.
            }
        }
    }
}
