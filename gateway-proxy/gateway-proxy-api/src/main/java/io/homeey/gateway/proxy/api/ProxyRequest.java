package io.homeey.gateway.proxy.api;

import java.util.Map;

public record ProxyRequest(
        String method,
        String url,
        Map<String, String> headers,
        byte[] body
) {
}
