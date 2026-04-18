package io.homeey.gateway.proxy.okhttp;

import io.homeey.gateway.proxy.api.ProxyClient;
import io.homeey.gateway.proxy.api.ProxyClientFactory;

public final class OkHttpProxyClientFactory implements ProxyClientFactory {
    @Override
    public ProxyClient create() {
        return new OkHttpProxyClient();
    }
}
