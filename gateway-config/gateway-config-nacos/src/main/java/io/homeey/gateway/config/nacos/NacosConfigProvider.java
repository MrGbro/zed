package io.homeey.gateway.config.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import io.homeey.gateway.config.api.ConfigProvider;

import java.util.Properties;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Nacos配置提供者实现，基于Nacos配置中心提供配置管理服务。
 * <p>
 * 该实现支持从Nacos获取、发布和订阅配置，当Nacos不可用时自动降级到内存配置提供者。
 * 内部维护配置快照，确保在连接失败时仍能提供最新的配置数据。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public final class NacosConfigProvider implements ConfigProvider {
    private final ConfigService nacosConfigService;
    private final InMemoryConfigProvider fallback;
    private final Map<String, String> snapshots = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<String>>> listeners = new ConcurrentHashMap<>();

    public NacosConfigProvider() {
        this((ConfigService) null, new InMemoryConfigProvider());
    }

    public NacosConfigProvider(String serverAddr) {
        this(createConfigServiceOrNull(serverAddr), new InMemoryConfigProvider());
    }

    public NacosConfigProvider(ConfigService nacosConfigService, InMemoryConfigProvider fallback) {
        this.nacosConfigService = nacosConfigService;
        this.fallback = fallback;
    }

    @Override
    public CompletionStage<String> get(String dataId, String group) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (nacosConfigService != null) {
                    String content = nacosConfigService.getConfig(dataId, group, 3000);
                    if (content != null) {
                        snapshots.put(key(dataId, group), content);
                        return content;
                    }
                }
            } catch (Exception ignored) {
                // fallback below
            }
            try {
                return fallback.get(dataId, group).toCompletableFuture().join();
            } catch (RuntimeException ex) {
                return snapshots.get(key(dataId, group));
            }
        });
    }

    @Override
    public CompletionStage<Boolean> publish(String dataId, String group, String content) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (nacosConfigService != null) {
                    boolean ok = nacosConfigService.publishConfig(dataId, group, content);
                    if (ok) {
                        snapshots.put(key(dataId, group), content);
                    }
                    return ok;
                }
            } catch (Exception ignored) {
                // fallback below
            }
            return fallback.publish(dataId, group, content).toCompletableFuture().join();
        });
    }

    @Override
    public CompletionStage<Void> subscribe(String dataId, String group, Consumer<String> listener) {
        listeners.computeIfAbsent(key(dataId, group), unused -> new CopyOnWriteArrayList<>())
                .add(listener);
        return CompletableFuture.runAsync(() -> {
            try {
                if (nacosConfigService != null) {
                    nacosConfigService.addListener(dataId, group, new AbstractListener() {
                        @Override
                        public void receiveConfigInfo(String configInfo) {
                            onNacosConfigChanged(dataId, group, configInfo);
                        }
                    });
                } else {
                    fallback.subscribe(dataId, group, listener).toCompletableFuture().join();
                }
            } catch (Exception ignored) {
                fallback.subscribe(dataId, group, listener).toCompletableFuture().join();
            }
        });
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

    private static ConfigService createConfigServiceOrNull(String serverAddr) {
        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddr", serverAddr);
            return NacosFactory.createConfigService(properties);
        } catch (Exception e) {
            return null;
        }
    }
}
