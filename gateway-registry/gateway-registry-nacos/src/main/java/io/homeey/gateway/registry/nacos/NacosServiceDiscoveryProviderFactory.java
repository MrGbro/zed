package io.homeey.gateway.registry.nacos;

import io.homeey.gateway.registry.api.ServiceDiscoveryProvider;
import io.homeey.gateway.registry.api.ServiceDiscoveryProviderFactory;

public final class NacosServiceDiscoveryProviderFactory implements ServiceDiscoveryProviderFactory {
    @Override
    public ServiceDiscoveryProvider create(String serverAddr) {
        return new NacosServiceDiscoveryProvider(serverAddr);
    }
}
