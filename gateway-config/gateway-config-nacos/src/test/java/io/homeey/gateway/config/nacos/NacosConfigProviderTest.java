package io.homeey.gateway.config.nacos;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NacosConfigProviderTest {

    @Test
    void shouldUpdateSnapshotAndNotifySubscriber() {
        NacosConfigProvider provider = new NacosConfigProvider("127.0.0.1:65535");
        AtomicReference<String> callbackValue = new AtomicReference<>();

        provider.subscribe("routes.json", "DEFAULT_GROUP", callbackValue::set)
                .toCompletableFuture()
                .join();

        provider.onNacosConfigChanged("routes.json", "DEFAULT_GROUP", "{\"version\":\"v2\"}");

        String latest = provider.get("routes.json", "DEFAULT_GROUP")
                .toCompletableFuture()
                .join();
        assertEquals("{\"version\":\"v2\"}", latest);
        assertEquals("{\"version\":\"v2\"}", callbackValue.get());
    }

    @Test
    void shouldKeepCurrentSnapshotWhenNacosEventFails() {
        NacosConfigProvider provider = new NacosConfigProvider("127.0.0.1:65535");
        provider.onNacosConfigChanged("routes.json", "DEFAULT_GROUP", "{\"version\":\"v1\"}");

        provider.onNacosConfigChanged("routes.json", "DEFAULT_GROUP", null);

        String latest = provider.get("routes.json", "DEFAULT_GROUP")
                .toCompletableFuture()
                .join();
        assertEquals("{\"version\":\"v1\"}", latest);
    }
}
