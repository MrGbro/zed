package io.homeey.gateway.governance.state.local;

import io.homeey.gateway.governance.api.GovernanceStateStore;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class LocalGovernanceStateStore implements GovernanceStateStore {
    private final ConcurrentHashMap<String, Object> values = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(String key, Supplier<T> supplier, Class<T> type) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(type, "type");
        Object existing = values.computeIfAbsent(key, ignored -> supplier.get());
        if (!type.isInstance(existing)) {
            throw new IllegalStateException("State type mismatch for key " + key + ": " + existing.getClass().getName());
        }
        return (T) existing;
    }
}
