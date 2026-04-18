package io.homeey.gateway.core.runtime;

import io.homeey.gateway.core.route.RouteDefinition;
import io.homeey.gateway.core.route.RouteTableSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class RuntimeSnapshotManager {
    private final AtomicReference<Map<String, Object>> snapshotRef;
    private final AtomicReference<RouteTableSnapshot> routeSnapshotRef;
    private final Map<String, Long> metrics = new HashMap<>();

    public RuntimeSnapshotManager(Map<String, Object> initialSnapshot) {
        this.snapshotRef = new AtomicReference<>(Map.copyOf(initialSnapshot));
        String version = String.valueOf(initialSnapshot.getOrDefault("version", "v0"));
        this.routeSnapshotRef = new AtomicReference<>(new RouteTableSnapshot(version, List.of()));
        metrics.put("config_update_success", 0L);
        metrics.put("config_update_fail", 0L);
    }

    public void onConfigPublished(Supplier<Map<String, Object>> builder) {
        Map<String, Object> oldSnapshot = snapshotRef.get();
        try {
            Map<String, Object> newSnapshot = Map.copyOf(builder.get());
            snapshotRef.set(newSnapshot);
            metrics.compute("config_update_success", (k, v) -> v + 1);
        } catch (RuntimeException ex) {
            snapshotRef.set(oldSnapshot);
            metrics.compute("config_update_fail", (k, v) -> v + 1);
        }
    }

    public Map<String, Object> currentSnapshot() {
        return snapshotRef.get();
    }

    public RouteTableSnapshot currentRouteSnapshot() {
        return routeSnapshotRef.get();
    }

    public void onRouteSnapshotPublished(RouteTableSnapshot snapshot) {
        routeSnapshotRef.set(snapshot);
        metrics.compute("config_update_success", (k, v) -> v + 1);
    }

    public Map<String, Long> metrics() {
        return Map.copyOf(metrics);
    }
}
