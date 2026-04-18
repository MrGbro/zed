package io.homeey.gateway.proxy.api;

import java.util.Map;

public record ProxyResponse(
        int statusCode,
        Map<String, String> headers,
        byte[] body
) {
}
