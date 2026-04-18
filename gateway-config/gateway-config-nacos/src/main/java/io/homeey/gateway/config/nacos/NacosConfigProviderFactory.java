package io.homeey.gateway.config.nacos;

import io.homeey.gateway.config.api.ConfigProvider;
import io.homeey.gateway.config.api.ConfigProviderFactory;

public final class NacosConfigProviderFactory implements ConfigProviderFactory {
    @Override
    public ConfigProvider create(String serverAddr) {
        return new NacosConfigProvider(serverAddr);
    }
}
