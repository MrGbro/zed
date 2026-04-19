package io.homeey.gateway.registry.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import io.homeey.gateway.registry.api.ServiceDiscoveryProvider;

import java.util.Properties;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class NacosServiceDiscoveryProvider implements ServiceDiscoveryProvider {
    private final NamingService namingService;
    private final InMemoryServiceDiscoveryProvider fallback;
    private final Map<String, List<String>> snapshots = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<List<String>>>> listeners = new ConcurrentHashMap<>();

    public NacosServiceDiscoveryProvider() {
        this((NamingService) null, new InMemoryServiceDiscoveryProvider());
    }

    public NacosServiceDiscoveryProvider(String serverAddr) {
        this(createNamingServiceOrNull(serverAddr), new InMemoryServiceDiscoveryProvider());
    }

    public NacosServiceDiscoveryProvider(NamingService namingService, InMemoryServiceDiscoveryProvider fallback) {
        this.namingService = namingService;
        this.fallback = fallback;
    }

    @Override
    public CompletionStage<List<String>> getInstances(String serviceName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (namingService != null) {
                    List<Instance> instances = namingService.selectInstances(serviceName, true);
                    List<String> endpoints = instances.stream()
                            .map(instance -> instance.getIp() + ":" + instance.getPort())
                            .toList();
                    snapshots.put(serviceName, endpoints);
                    return endpoints;
                }
            } catch (Exception ignored) {
                // fallback below
            }
            List<String> fallbackInstances = fallback.getInstances(serviceName).toCompletableFuture().join();
            if (fallbackInstances != null && !fallbackInstances.isEmpty()) {
                return fallbackInstances;
            }
            return snapshots.getOrDefault(serviceName, List.of());
        });
    }

    @Override
    public CompletionStage<Void> register(String serviceName, String endpoint) {
        return CompletableFuture.runAsync(() -> {
            String[] parts = endpoint.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("endpoint must be ip:port");
            }
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);
            try {
                if (namingService != null) {
                    namingService.registerInstance(serviceName, ip, port);
                } else {
                    fallback.register(serviceName, endpoint).toCompletableFuture().join();
                }
            } catch (Exception ignored) {
                fallback.register(serviceName, endpoint).toCompletableFuture().join();
            }
        });
    }

    @Override
    public CompletionStage<Void> subscribe(String serviceName, Consumer<List<String>> listener) {
        listeners.computeIfAbsent(serviceName, unused -> new CopyOnWriteArrayList<>())
                .add(listener);
        return CompletableFuture.runAsync(() -> {
            try {
                if (namingService != null) {
                    namingService.subscribe(serviceName, new EventListener() {
                        @Override
                        public void onEvent(Event event) {
                            if (event instanceof NamingEvent namingEvent) {
                                List<String> endpoints = namingEvent.getInstances().stream()
                                        .map(instance -> instance.getIp() + ":" + instance.getPort())
                                        .collect(Collectors.toList());
                                onNacosInstancesChanged(serviceName, endpoints);
                            }
                        }
                    });
                } else {
                    fallback.subscribe(serviceName, listener).toCompletableFuture().join();
                }
            } catch (Exception ignored) {
                fallback.subscribe(serviceName, listener).toCompletableFuture().join();
            }
        });
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

    private static NamingService createNamingServiceOrNull(String serverAddr) {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverAddr);
        try {
            return NacosFactory.createNamingService(properties);
        } catch (NacosException e) {
            return null;
        }
    }
}
