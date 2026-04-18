package io.homeey.gateway.bootstrap;

import io.homeey.gateway.bootstrap.config.BootstrapConfig;
import io.homeey.gateway.bootstrap.config.BootstrapConfigLoader;
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

/**
 * 网关引导应用类，负责网关的初始化、启动和停止。
 * <p>
 * 该类是网关运行的核心入口，管理运行时组件的生命周期，包括：
 * <ul>
 *   <li>初始化配置提供者和服务发现提供者</li>
 *   <li>订阅路由配置变更</li>
 *   <li>管理过滤器生命周期（init/start/stop）</li>
 *   <li>启动和停止传输服务器</li>
 * </ul>
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public final class BootstrapApplication {
    private final RuntimeFactory runtimeFactory;
    private RuntimeFactory.RuntimeComponents runtimeComponents;
    private TransportServer runningTransport;
    private final List<GatewayFilter> lifecycleFilters = new ArrayList<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * 构造引导应用。
     *
     * @param runtimeFactory 运行时工厂，用于创建网关运行时组件
     */
    public BootstrapApplication(RuntimeFactory runtimeFactory) {
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
    }

    /**
     * 初始化网关应用。
     * <p>
     * 创建运行时组件、初始化过滤器生命周期、加载初始路由配置并订阅配置变更。
     * </p>
     *
     * @param config 引导配置
     */
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

    /**
     * 启动网关应用。
     * <p>
     * 启动过滤器生命周期和传输服务器，开始接收请求。
     * </p>
     *
     * @throws IllegalStateException 如果应用尚未初始化
     */
    public void start() {
        if (!initialized.get()) {
            throw new IllegalStateException("Application must be initialized before start");
        }
        startFilterLifecycle();
        this.runningTransport.start().toCompletableFuture().join();
    }

    /**
     * 停止网关应用。
     * <p>
     * 停止传输服务器、过滤器生命周期，并关闭代理客户端。
     * </p>
     */
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

    /**
     * 初始化并启动网关应用。
     * <p>
     * 等价于依次调用 {@link #init(BootstrapConfig)} 和 {@link #start()}。
     * </p>
     *
     * @param config 引导配置
     */
    public void run(BootstrapConfig config) {
        init(config);
        start();
    }

    public void run() {
        run(BootstrapConfigLoader.load());
    }

    /**
     * 关闭网关应用。
     * <p>
     * 等价于调用 {@link #stop()}。
     * </p>
     */
    public void shutdown() {
        stop();
    }

    public static void main(String[] args) {
        BootstrapApplication app = new BootstrapApplication(new RuntimeFactory());
        app.run();
        Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
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
