package io.homeey.gateway.registry.nacos;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NacosServiceDiscoveryProviderTest {

    @Test
    void shouldUpdateSnapshotAndNotifySubscriber() {
        NacosServiceDiscoveryProvider provider = new NacosServiceDiscoveryProvider();
        AtomicReference<List<String>> callbackValue = new AtomicReference<>();

        provider.subscribe("order-service", callbackValue::set)
                .toCompletableFuture()
                .join();

        provider.onNacosInstancesChanged("order-service", List.of("10.0.0.1:8080", "10.0.0.2:8080"));

        List<String> latest = provider.getInstances("order-service")
                .toCompletableFuture()
                .join();
        assertEquals(List.of("10.0.0.1:8080", "10.0.0.2:8080"), latest);
        assertEquals(List.of("10.0.0.1:8080", "10.0.0.2:8080"), callbackValue.get());
    }

    @Test
    void shouldKeepCurrentSnapshotWhenNacosEventFails() {
        NacosServiceDiscoveryProvider provider = new NacosServiceDiscoveryProvider();
        provider.onNacosInstancesChanged("order-service", List.of("10.0.0.1:8080"));

        provider.onNacosInstancesChanged("order-service", null);

        List<String> latest = provider.getInstances("order-service")
                .toCompletableFuture()
                .join();
        assertEquals(List.of("10.0.0.1:8080"), latest);
    }
}
