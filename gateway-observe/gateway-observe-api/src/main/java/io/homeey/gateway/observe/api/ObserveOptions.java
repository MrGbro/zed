package io.homeey.gateway.observe.api;

import java.util.Map;

/**
 * Observe provider runtime options from bootstrap configuration.
 */
public record ObserveOptions(
        String providerType,
        String otlpEndpoint,
        Map<String, String> otlpHeaders,
        String serviceName,
        long exportIntervalMillis,
        String metricsPath,
        boolean accessLogEnabled
) {
    public static final String DEFAULT_PROVIDER_TYPE = "otel";
    public static final String DEFAULT_OTLP_ENDPOINT = "";
    public static final String DEFAULT_SERVICE_NAME = "gateway-node";
    public static final long DEFAULT_EXPORT_INTERVAL_MILLIS = 10_000L;
    public static final String DEFAULT_METRICS_PATH = "/metrics";
    public static final boolean DEFAULT_ACCESS_LOG_ENABLED = true;

    public ObserveOptions {
        providerType = isBlank(providerType) ? DEFAULT_PROVIDER_TYPE : providerType;
        otlpEndpoint = otlpEndpoint == null ? DEFAULT_OTLP_ENDPOINT : otlpEndpoint;
        otlpHeaders = otlpHeaders == null ? Map.of() : Map.copyOf(otlpHeaders);
        serviceName = isBlank(serviceName) ? DEFAULT_SERVICE_NAME : serviceName;
        exportIntervalMillis = exportIntervalMillis <= 0 ? DEFAULT_EXPORT_INTERVAL_MILLIS : exportIntervalMillis;
        metricsPath = normalizeMetricsPath(metricsPath);
    }

    public static ObserveOptions defaults() {
        return new ObserveOptions(
                DEFAULT_PROVIDER_TYPE,
                DEFAULT_OTLP_ENDPOINT,
                Map.of(),
                DEFAULT_SERVICE_NAME,
                DEFAULT_EXPORT_INTERVAL_MILLIS,
                DEFAULT_METRICS_PATH,
                DEFAULT_ACCESS_LOG_ENABLED
        );
    }

    private static String normalizeMetricsPath(String value) {
        String path = isBlank(value) ? DEFAULT_METRICS_PATH : value.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
