package io.homeey.gateway.registry.nacos;

import io.homeey.gateway.registry.api.ServiceDiscoveryProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class InMemoryServiceDiscoveryProvider implements ServiceDiscoveryProvider {
    private final Map<String, List<String>> snapshots = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<List<String>>>> listeners = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<List<String>> getInstances(String serviceName) {
        return CompletableFuture.completedFuture(snapshots.getOrDefault(serviceName, List.of()));
    }

    @Override
    public CompletionStage<Void> register(String serviceName, String endpoint) {
        snapshots.compute(serviceName, (name, values) -> {
            List<String> next = new ArrayList<>(values == null ? List.of() : values);
            if (!next.contains(endpoint)) {
                next.add(endpoint);
            }
            return List.copyOf(next);
        });
        notifyListeners(serviceName);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> subscribe(String serviceName, Consumer<List<String>> listener) {
        listeners.computeIfAbsent(serviceName, unused -> new CopyOnWriteArrayList<>())
                .add(listener);
        return CompletableFuture.completedFuture(null);
    }

    private void notifyListeners(String serviceName) {
        List<String> current = snapshots.getOrDefault(serviceName, List.of());
        for (Consumer<List<String>> listener : listeners.getOrDefault(serviceName, List.of())) {
            try {
                listener.accept(current);
            } catch (RuntimeException ignored) {
                // Keep serving existing snapshot.
            }
        }
    }
}
