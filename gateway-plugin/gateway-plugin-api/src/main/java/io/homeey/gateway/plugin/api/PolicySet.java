package io.homeey.gateway.plugin.api;

import java.util.Collections;
import java.util.Map;

public final class PolicySet {
    private final Map<String, Object> entries;

    public PolicySet(Map<String, Object> entries) {
        this.entries = entries == null ? Map.of() : Collections.unmodifiableMap(entries);
    }

    public Map<String, Object> entries() {
        return entries;
    }
}
