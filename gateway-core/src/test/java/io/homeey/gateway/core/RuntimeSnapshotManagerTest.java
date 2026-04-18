package io.homeey.gateway.core;

import io.homeey.gateway.core.runtime.RuntimeSnapshotManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeSnapshotManagerTest {

    @Test
    void shouldReplaceSnapshotWhenConfigPublished() {
        RuntimeSnapshotManager manager = new RuntimeSnapshotManager(Map.of("version", "v1"));

        manager.onConfigPublished(() -> Map.of("version", "v2"));

        assertEquals("v2", manager.currentSnapshot().get("version"));
        assertEquals(1L, manager.metrics().get("config_update_success"));
        assertEquals(0L, manager.metrics().get("config_update_fail"));
    }

    @Test
    void shouldRollbackWhenBuildFails() {
        RuntimeSnapshotManager manager = new RuntimeSnapshotManager(Map.of("version", "v1"));

        manager.onConfigPublished(() -> {
            throw new IllegalStateException("broken");
        });

        assertEquals("v1", manager.currentSnapshot().get("version"));
        assertEquals(0L, manager.metrics().get("config_update_success"));
        assertEquals(1L, manager.metrics().get("config_update_fail"));
    }
}
