package io.homeey.gateway.config.nacos;

import io.homeey.gateway.config.api.ConfigProvider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 内存配置提供者实现，用于测试和降级场景。
 * <p>
 * 该实现在内存中维护配置快照和监听器列表，不提供持久化能力。
 * 当Nacos配置中心不可用时，可作为降级方案使用。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public final class InMemoryConfigProvider implements ConfigProvider {
    private final Map<String, String> snapshots = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<String>>> listeners = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<String> get(String dataId, String group) {
        return CompletableFuture.completedFuture(snapshots.get(key(dataId, group)));
    }

    @Override
    public CompletionStage<Boolean> publish(String dataId, String group, String content) {
        if (content == null) {
            return CompletableFuture.completedFuture(false);
        }
        String key = key(dataId, group);
        snapshots.put(key, content);
        for (Consumer<String> listener : listeners.getOrDefault(key, List.of())) {
            try {
                listener.accept(content);
            } catch (RuntimeException ignored) {
                // Keep serving existing snapshot.
            }
        }
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletionStage<Void> subscribe(String dataId, String group, Consumer<String> listener) {
        listeners.computeIfAbsent(key(dataId, group), unused -> new CopyOnWriteArrayList<>())
                .add(listener);
        return CompletableFuture.completedFuture(null);
    }

    private static String key(String dataId, String group) {
        return group + ":" + dataId;
    }
}
