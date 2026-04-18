package io.homeey.gateway.admin.config;

import io.homeey.gateway.common.spi.ExtensionLoader;
import io.homeey.gateway.config.api.ConfigProvider;
import io.homeey.gateway.config.api.ConfigProviderFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
