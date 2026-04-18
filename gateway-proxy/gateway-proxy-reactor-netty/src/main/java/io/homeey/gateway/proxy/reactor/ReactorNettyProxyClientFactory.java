package io.homeey.gateway.proxy.reactor;

import io.homeey.gateway.proxy.api.ProxyClient;
import io.homeey.gateway.proxy.api.ProxyClientFactory;

public final class ReactorNettyProxyClientFactory implements ProxyClientFactory {
    @Override
    public ProxyClient create() {
        return new ReactorNettyProxyClient();
    }
}
