package io.homeey.gateway.common.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Attributes {
    private final Map<String, Object> values = new ConcurrentHashMap<>();

    public void put(String key, Object value) {
        values.put(key, value);
    }

    public Object get(String key) {
        return values.get(key);
    }
}