package io.homeey.gateway.proxy.asynchttp;

import io.homeey.gateway.proxy.api.ProxyClient;
import io.homeey.gateway.proxy.api.ProxyClientFactory;

public final class AsyncHttpProxyClientFactory implements ProxyClientFactory {
    @Override
    public ProxyClient create() {
        return new AsyncHttpProxyClient();
    }
}
