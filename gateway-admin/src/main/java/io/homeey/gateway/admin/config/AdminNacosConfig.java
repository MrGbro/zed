package io.homeey.gateway.admin.config;

import io.homeey.gateway.common.spi.ExtensionLoader;
import io.homeey.gateway.config.api.ConfigProvider;
import io.homeey.gateway.config.api.ConfigProviderFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 管理端Nacos配置类，提供配置提供者的Spring Bean。
 * <p>
 * 该配置类通过SPI机制创建ConfigProvider实例，支持Nacos连接失败时的降级处理。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
@Configuration
public class AdminNacosConfig {

    @Bean
    public ConfigProvider configProvider() {
        ExtensionLoader<ConfigProviderFactory> loader = ExtensionLoader.getExtensionLoader(ConfigProviderFactory.class);
        try {
            return loader.getDefaultExtension().create("192.168.79.144:8848");
        } catch (RuntimeException ex) {
            return loader.getDefaultExtension().create(null);
        }
    }
}
