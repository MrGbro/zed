package io.homeey.gateway.bootstrap.wiring;

import io.homeey.gateway.bootstrap.config.BootstrapConfig;
import io.homeey.gateway.common.spi.ExtensionLoader;
import io.homeey.gateway.config.api.ConfigProvider;
import io.homeey.gateway.config.api.ConfigProviderFactory;
import io.homeey.gateway.core.runtime.RouteSnapshotCodec;
import io.homeey.gateway.core.runtime.RuntimeSnapshotManager;
import io.homeey.gateway.proxy.api.ProxyClient;
import io.homeey.gateway.proxy.api.ProxyClientFactory;
import io.homeey.gateway.registry.api.ServiceDiscoveryProvider;
import io.homeey.gateway.registry.api.ServiceDiscoveryProviderFactory;
import io.homeey.gateway.transport.api.GatewayRequestHandler;
import io.homeey.gateway.transport.api.TransportServer;
import io.homeey.gateway.transport.api.TransportServerFactory;

import java.util.Map;

/**
 * 运行时工厂，负责创建和组装网关的运行时组件。
 * <p>
 * 该工厂根据引导配置创建传输服务器、代理客户端、配置提供者、服务发现提供者等核心组件，
 * 并将它们组装成完整的运行时环境。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public final class RuntimeFactory {
    public record RuntimeComponents(
            TransportServer transportServer,
            ProxyClient proxyClient,
            ConfigProvider configProvider,
            ServiceDiscoveryProvider discoveryProvider,
            RuntimeSnapshotManager snapshotManager,
            RouteSnapshotCodec routeSnapshotCodec
    ) {
    }

    public RuntimeFactory() {
        // no-op
    }

    public RuntimeComponents createRuntime(BootstrapConfig config) {
        ConfigProvider configProvider = createConfigProvider(config);
        ServiceDiscoveryProvider discoveryProvider = createDiscoveryProvider(config);
        RuntimeSnapshotManager snapshotManager = new RuntimeSnapshotManager(Map.of("version", "v0"));
        RouteSnapshotCodec snapshotCodec = new RouteSnapshotCodec();
        ProxyClient proxyClient = createProxyClient(config);

        DefaultGatewayRequestHandler handler = new DefaultGatewayRequestHandler(
                snapshotManager,
                discoveryProvider,
                proxyClient
        );

        TransportServer transport = createTransport(config, handler);
        return new RuntimeComponents(
                transport,
                proxyClient,
                configProvider,
                discoveryProvider,
                snapshotManager,
                snapshotCodec
        );
    }

    private ConfigProvider createConfigProvider(BootstrapConfig config) {
        ExtensionLoader<ConfigProviderFactory> loader = ExtensionLoader.getExtensionLoader(ConfigProviderFactory.class);
        if (isBlank(config.configProviderType())) {
            return withFallback(loader.getDefaultExtension()::create, config.nacosServerAddr());
        }
        return withFallback(addr -> loader.getExtension(config.configProviderType()).create(addr), config.nacosServerAddr());
    }

    private ServiceDiscoveryProvider createDiscoveryProvider(BootstrapConfig config) {
        ExtensionLoader<ServiceDiscoveryProviderFactory> loader =
                ExtensionLoader.getExtensionLoader(ServiceDiscoveryProviderFactory.class);
        if (isBlank(config.discoveryProviderType())) {
            return withFallback(loader.getDefaultExtension()::create, config.nacosServerAddr());
        }
        return withFallback(addr -> loader.getExtension(config.discoveryProviderType()).create(addr), config.nacosServerAddr());
    }

    private ProxyClient createProxyClient(BootstrapConfig config) {
        ExtensionLoader<ProxyClientFactory> loader = ExtensionLoader.getExtensionLoader(ProxyClientFactory.class);
        if (isBlank(config.proxyClientType())) {
            return loader.getDefaultExtension().create();
        }
        return loader.getExtension(config.proxyClientType()).create();
    }

    private TransportServer createTransport(BootstrapConfig config, GatewayRequestHandler handler) {
        ExtensionLoader<TransportServerFactory> loader = ExtensionLoader.getExtensionLoader(TransportServerFactory.class);
        if (isBlank(config.transportType())) {
            return loader.getDefaultExtension().create(config.port(), handler);
        }
        return loader.getExtension(config.transportType()).create(config.port(), handler);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private <T> T withFallback(java.util.function.Function<String, T> builder, String serverAddr) {
        try {
            return builder.apply(serverAddr);
        } catch (RuntimeException ex) {
            return builder.apply(null);
        }
    }
}
