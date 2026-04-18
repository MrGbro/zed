package io.homeey.gateway.core.route;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class RadixRouteLocator {
    private final RouteTableSnapshot snapshot;

    public RadixRouteLocator(RouteTableSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public Optional<RouteDefinition> locate(
            String host,
            String path,
            String method,
            Map<String, String> headers
    ) {
        for (RouteDefinition route : snapshot.routes()) {
            if (!route.host().equalsIgnoreCase(host)) {
                continue;
            }
            if (!path.startsWith(route.pathPrefix())) {
                continue;
            }
            if (!route.method().equalsIgnoreCase(method)) {
                continue;
            }
            if (!headersMatch(route.headers(), headers)) {
                continue;
            }
            return Optional.of(route);
        }
        return Optional.empty();
    }

    private boolean headersMatch(Map<String, String> required, Map<String, String> actual) {
        for (Map.Entry<String, String> entry : required.entrySet()) {
            String key = entry.getKey();
            String expected = entry.getValue();
            String actualValue = findIgnoreCase(actual, key);
            if (actualValue == null || !expected.equals(actualValue)) {
                return false;
            }
        }
        return true;
    }

    private String findIgnoreCase(Map<String, String> headers, String key) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(key.toLowerCase(Locale.ROOT))) {
                return entry.getValue();
            }
        }
        return null;
    }
}
