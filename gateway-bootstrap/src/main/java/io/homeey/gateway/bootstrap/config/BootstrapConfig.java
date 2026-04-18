package io.homeey.gateway.bootstrap.config;

/**
 * 引导配置记录，封装网关启动所需的配置参数。
 * <p>
 * 包含传输层、配置中心、服务发现、代理客户端等组件的类型和连接信息。
 * </p>
 *
 * @param transportType          传输服务器类型（如netty）
 * @param configProviderType     配置提供者类型（如nacos）
 * @param discoveryProviderType  服务发现提供者类型（如nacos）
 * @param proxyClientType        代理客户端类型（如async-http-client）
 * @param port                   网关监听端口
 * @param nacosServerAddr        Nacos服务器地址
 * @param routesDataId           路由配置的dataId
 * @param group                  配置分组
 * @param gracefulTimeoutMillis  优雅关闭超时时间（毫秒）
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
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
    public static final String DEFAULT_TRANSPORT_TYPE = "netty";
    public static final String DEFAULT_CONFIG_PROVIDER_TYPE = "nacos";
    public static final String DEFAULT_DISCOVERY_PROVIDER_TYPE = "nacos";
    public static final String DEFAULT_PROXY_CLIENT_TYPE = "async-http-client";
    public static final int DEFAULT_PORT = 18080;
    public static final String DEFAULT_NACOS_SERVER_ADDR = "192.168.79.144:8848";
    public static final String DEFAULT_ROUTES_DATA_ID = "gateway.routes.json";
    public static final String DEFAULT_GROUP = "GATEWAY";
    public static final long DEFAULT_GRACEFUL_TIMEOUT_MILLIS = 5000L;

    public static BootstrapConfig defaultConfig() {
        return new BootstrapConfig(
                DEFAULT_TRANSPORT_TYPE,
                DEFAULT_CONFIG_PROVIDER_TYPE,
                DEFAULT_DISCOVERY_PROVIDER_TYPE,
                DEFAULT_PROXY_CLIENT_TYPE,
                DEFAULT_PORT,
                DEFAULT_NACOS_SERVER_ADDR,
                DEFAULT_ROUTES_DATA_ID,
                DEFAULT_GROUP,
                DEFAULT_GRACEFUL_TIMEOUT_MILLIS
        );
    }


    /**
     * 创建默认引导配置。
     *
     * @param transportType 传输服务器类型
     * @param port          网关监听端口
     * @return 引导配置实例
     */
    public static BootstrapConfig of(String transportType, int port) {
        return new BootstrapConfig(
                transportType,
                DEFAULT_CONFIG_PROVIDER_TYPE,
                DEFAULT_DISCOVERY_PROVIDER_TYPE,
                DEFAULT_PROXY_CLIENT_TYPE,
                port,
                DEFAULT_NACOS_SERVER_ADDR,
                DEFAULT_ROUTES_DATA_ID,
                DEFAULT_GROUP,
                DEFAULT_GRACEFUL_TIMEOUT_MILLIS
        );
    }
}
