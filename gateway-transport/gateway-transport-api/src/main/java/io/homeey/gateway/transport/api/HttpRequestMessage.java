package io.homeey.gateway.transport.api;

import java.util.Map;

public record HttpRequestMessage(
        String method,
        String host,
        String path,
        String query,
        Map<String, String> headers,
        byte[] body
) {
}
