package io.homeey.gateway.bootstrap;

import io.homeey.gateway.bootstrap.config.BootstrapConfig;
import io.homeey.gateway.common.spi.ExtensionLoader;
import io.homeey.gateway.bootstrap.wiring.RuntimeFactory;
import io.homeey.gateway.core.route.RouteTableSnapshot;
import io.homeey.gateway.core.runtime.SnapshotCodecException;
import io.homeey.gateway.plugin.api.GatewayFilter;
import io.homeey.gateway.transport.api.TransportServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BootstrapApplication {
    private final RuntimeFactory runtimeFactory;
    private RuntimeFactory.RuntimeComponents runtimeComponents;
    private TransportServer runningTransport;
    private final List<GatewayFilter> lifecycleFilters = new ArrayList<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public BootstrapApplication(RuntimeFactory runtimeFactory) {
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
    }

    public void init(BootstrapConfig config) {
        this.runtimeComponents = runtimeFactory.createRuntime(config);
        this.runningTransport = runtimeComponents.transportServer();
        initFilterLifecycle();
        String initial = runtimeComponents.configProvider()
                .get(config.routesDataId(), config.group())
                .toCompletableFuture()
                .join();
        if (initial != null && !initial.isBlank()) {
            try {
                RouteTableSnapshot snapshot = runtimeComponents.routeSnapshotCodec().decode(initial);
                runtimeComponents.snapshotManager().onRouteSnapshotPublished(snapshot);
            } catch (SnapshotCodecException ignored) {
                // Keep empty snapshot on invalid initial config.
            }
        }
        runtimeComponents.configProvider()
                .subscribe(config.routesDataId(), config.group(), content -> {
                    if (content == null || content.isBlank()) {
                        return;
                    }
                    try {
                        RouteTableSnapshot snapshot = runtimeComponents.routeSnapshotCodec().decode(content);
                        runtimeComponents.snapshotManager().onRouteSnapshotPublished(snapshot);
                    } catch (SnapshotCodecException ignored) {
                        // Keep old snapshot.
                    }
                })
                .toCompletableFuture()
                .join();
        initialized.set(true);
    }

    public void start() {
        if (!initialized.get()) {
            throw new IllegalStateException("Application must be initialized before start");
        }
        startFilterLifecycle();
        this.runningTransport.start().toCompletableFuture().join();
    }

    public void stop() {
        if (runningTransport != null) {
            runningTransport.stop().toCompletableFuture().join();
        }
        stopFilterLifecycle();
        if (runtimeComponents != null) {
            runtimeComponents.proxyClient().close();
        }
        initialized.set(false);
    }

    public void run(BootstrapConfig config) {
        init(config);
        start();
    }

    public void shutdown() {
        stop();
    }

    private void initFilterLifecycle() {
        lifecycleFilters.clear();
        ExtensionLoader<GatewayFilter> loader = ExtensionLoader.getExtensionLoader(GatewayFilter.class);
        for (String name : loader.getSupportedExtensions()) {
            GatewayFilter filter = loader.getExtension(name);
            filter.init();
            lifecycleFilters.add(filter);
        }
    }

    private void startFilterLifecycle() {
        for (GatewayFilter filter : lifecycleFilters) {
            filter.start();
        }
    }

    private void stopFilterLifecycle() {
        List<GatewayFilter> reversed = new ArrayList<>(lifecycleFilters);
        Collections.reverse(reversed);
        for (GatewayFilter filter : reversed) {
            try {
                filter.stop();
            } catch (RuntimeException ignored) {
                // no-op
            }
        }
        lifecycleFilters.clear();
    }
}
