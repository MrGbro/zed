package io.homeey.gateway.observe.api;

import io.homeey.gateway.transport.api.HttpRequestMessage;
import io.homeey.gateway.transport.api.HttpResponseMessage;

import java.util.Map;
import java.util.Objects;

/**
 * Request-scoped observability contract.
 */
public interface RequestObservation extends AutoCloseable {
    String traceId();

    void onRouteMatched(String routeId);

    void onUpstreamSelected(String upstream);

    void onError(Throwable throwable, String category);

    void onResponse(HttpResponseMessage response);

    @Override
    void close();

    static RequestObservation noop(HttpRequestMessage request) {
        Objects.requireNonNull(request, "request");
        return new RequestObservation() {
            @Override
            public String traceId() {
                return "";
            }

            @Override
            public void onRouteMatched(String routeId) {
                // no-op
            }

            @Override
            public void onUpstreamSelected(String upstream) {
                // no-op
            }

            @Override
            public void onError(Throwable throwable, String category) {
                // no-op
            }

            @Override
            public void onResponse(HttpResponseMessage response) {
                // no-op
            }

            @Override
            public void close() {
                // no-op
            }
        };
    }

    static HttpResponseMessage metricsResponse(String payload) {
        byte[] body = payload == null ? new byte[0] : payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new HttpResponseMessage(
                200,
                Map.of("content-type", "text/plain; version=0.0.4; charset=UTF-8"),
                body
        );
    }
}
