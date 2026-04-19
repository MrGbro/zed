package io.homeey.gateway.observe.otel;

import io.homeey.gateway.observe.api.ObserveOptions;
import io.homeey.gateway.observe.api.RequestObservation;
import io.homeey.gateway.transport.api.HttpRequestMessage;
import io.homeey.gateway.transport.api.HttpResponseMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtelObserveProviderTest {

    @Test
    void shouldGenerateTraceAndExposePrometheusSnapshot() {
        OtelObserveProvider provider = new OtelObserveProvider(ObserveOptions.defaults());
        provider.init();
        provider.start();
        try {
            RequestObservation observation = provider.begin(new HttpRequestMessage(
                    "GET",
                    "example.com",
                    "/orders",
                    "",
                    Map.of(),
                    new byte[0]
            ));
            observation.onRouteMatched("r1");
            observation.onUpstreamSelected("order-service");
            observation.onResponse(new HttpResponseMessage(
                    200,
                    Map.of(),
                    "ok".getBytes(StandardCharsets.UTF_8)
            ));
            observation.close();

            String traceId = observation.traceId();
            assertNotNull(traceId);
            assertFalse(traceId.isBlank());

            String snapshot = provider.metricsSnapshot();
            assertTrue(snapshot.contains("gateway_requests_total"));
            assertTrue(snapshot.contains("gateway_request_duration_ms"));
        } finally {
            provider.stop();
        }
    }

    @Test
    void shouldRemainFailOpenOnInvalidOtlpEndpoint() {
        ObserveOptions options = new ObserveOptions(
                "otel",
                "http://invalid-endpoint",
                Map.of(),
                "gateway-node",
                1000,
                "/metrics",
                true
        );
        OtelObserveProvider provider = new OtelObserveProvider(options);
        provider.init();
        provider.start();
        try {
            RequestObservation observation = provider.begin(new HttpRequestMessage(
                    "GET",
                    "example.com",
                    "/ping",
                    "",
                    Map.of(),
                    new byte[0]
            ));
            observation.onResponse(new HttpResponseMessage(200, Map.of(), new byte[0]));
            observation.close();
            assertTrue(provider.metricsSnapshot().contains("gateway_requests_total"));
        } finally {
            provider.stop();
        }
    }
}
