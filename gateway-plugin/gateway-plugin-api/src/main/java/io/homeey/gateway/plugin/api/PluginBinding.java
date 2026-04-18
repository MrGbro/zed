package io.homeey.gateway.plugin.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class PluginBinding {
    private final String name;
    private final String routeId;
    private final int order;
    private final boolean enabled;
    private final FilterFailPolicy failPolicy;
    private final Map<String, Object> config;

    public PluginBinding(
            String name,
            String routeId,
            int order,
            boolean enabled,
            FilterFailPolicy failPolicy,
            Map<String, Object> config
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.routeId = routeId;
        this.order = order;
        this.enabled = enabled;
        this.failPolicy = failPolicy == null ? FilterFailPolicy.FAIL_CLOSE : failPolicy;
        this.config = config == null ? Map.of() : Collections.unmodifiableMap(config);
    }

    public String name() {
        return name;
    }

    public String routeId() {
        return routeId;
    }

    public int order() {
        return order;
    }

    public boolean enabled() {
        return enabled;
    }

    public FilterFailPolicy failPolicy() {
        return failPolicy;
    }

    public Map<String, Object> config() {
        return config;
    }
}
