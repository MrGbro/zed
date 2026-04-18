package io.homeey.gateway.config.api;

import io.homeey.gateway.common.spi.SPI;

/**
 * 配置提供者工厂接口，用于创建 {@link ConfigProvider} 实例。
 * <p>
 * 该接口通过SPI机制支持多种配置中心的实现，默认使用Nacos配置中心。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
@SPI("nacos")
public interface ConfigProviderFactory {
    /**
     * 创建配置提供者实例。
     *
     * @param serverAddr 配置中心服务器地址
     * @return 配置提供者实例
     */
    ConfigProvider create(String serverAddr);
}
