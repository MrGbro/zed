package io.homeey.gateway.core.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class RuntimeSnapshotManager {
    private final AtomicReference<Map<String, Object>> snapshotRef;
    private final Map<String, Long> metrics = new HashMap<>();

    public RuntimeSnapshotManager(Map<String, Object> initialSnapshot) {
        this.snapshotRef = new AtomicReference<>(Map.copyOf(initialSnapshot));
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

    public Map<String, Long> metrics() {
        return Map.copyOf(metrics);
    }
}
