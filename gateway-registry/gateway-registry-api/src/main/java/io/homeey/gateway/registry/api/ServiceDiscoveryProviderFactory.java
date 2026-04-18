package io.homeey.gateway.registry.api;

import io.homeey.gateway.common.spi.SPI;

/**
 * 服务发现提供者工厂接口，用于创建 {@link ServiceDiscoveryProvider} 实例。
 * <p>
 * 该接口通过SPI机制支持多种服务注册中心的实现，默认使用Nacos注册中心。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
@SPI("nacos")
public interface ServiceDiscoveryProviderFactory {
    /**
     * 创建服务发现提供者实例。
     *
     * @param serverAddr 注册中心服务器地址
     * @return 服务发现提供者实例
     */
    ServiceDiscoveryProvider create(String serverAddr);
}
