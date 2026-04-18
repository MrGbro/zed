package io.homeey.gateway.transport.api;

import java.util.Map;

public record HttpResponseMessage(
        int statusCode,
        Map<String, String> headers,
        byte[] body
) {
}
