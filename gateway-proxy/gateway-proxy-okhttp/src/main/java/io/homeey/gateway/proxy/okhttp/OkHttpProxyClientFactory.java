package io.homeey.gateway.proxy.okhttp;

import io.homeey.gateway.proxy.api.ProxyClient;
import io.homeey.gateway.proxy.api.ProxyClientFactory;

/**
 * OkHttp代理客户端工厂，用于创建 {@link OkHttpProxyClient} 实例。
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public final class OkHttpProxyClientFactory implements ProxyClientFactory {
    @Override
    public ProxyClient create() {
        return new OkHttpProxyClient();
    }
}
