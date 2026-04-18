package io.homeey.gateway.core.runtime;

import java.util.Map;

public final class GatewayRuntime {
    private final RuntimeSnapshotManager snapshotManager;

    public GatewayRuntime(Map<String, Object> initialSnapshot) {
        this.snapshotManager = new RuntimeSnapshotManager(initialSnapshot);
    }

    public RuntimeSnapshotManager snapshotManager() {
        return snapshotManager;
    }
}
