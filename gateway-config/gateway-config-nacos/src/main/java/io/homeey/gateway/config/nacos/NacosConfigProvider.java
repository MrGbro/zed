package io.homeey.gateway.config.nacos;

import io.homeey.gateway.config.api.ConfigProvider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class NacosConfigProvider implements ConfigProvider {
    private final Map<String, String> snapshots = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<String>>> listeners = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<String> get(String dataId, String group) {
        return CompletableFuture.completedFuture(snapshots.get(key(dataId, group)));
    }

    @Override
    public CompletionStage<Void> subscribe(String dataId, String group, Consumer<String> listener) {
        listeners.computeIfAbsent(key(dataId, group), unused -> new CopyOnWriteArrayList<>())
                .add(listener);
        return CompletableFuture.completedFuture(null);
    }

    public void onNacosConfigChanged(String dataId, String group, String content) {
        if (content == null) {
            return;
        }
        String key = key(dataId, group);
        snapshots.put(key, content);
        for (Consumer<String> listener : listeners.getOrDefault(key, List.of())) {
            try {
                listener.accept(content);
            } catch (RuntimeException ignored) {
                // Keep current snapshot when callback fails.
            }
        }
    }

    private static String key(String dataId, String group) {
        return group + ":" + dataId;
    }
}
