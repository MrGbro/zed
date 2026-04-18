package io.homeey.gateway.bootstrap.config;

public record BootstrapConfig(
        String transportType,
        String configProviderType,
        String discoveryProviderType,
        String proxyClientType,
        int port,
        String nacosServerAddr,
        String routesDataId,
        String group,
        long gracefulTimeoutMillis
) {

    public static BootstrapConfig of(String transportType, int port) {
        return new BootstrapConfig(
                transportType,
                "nacos",
                "nacos",
                "async-http-client",
                port,
                "192.168.79.144:8848",
                "gateway.routes.json",
                "GATEWAY",
                5000
        );
    }
}
