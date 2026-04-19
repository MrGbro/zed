package io.homeey.gateway.plugin.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    @JsonCreator
    public PluginBinding(
            @JsonProperty("name") String name,
            @JsonProperty("routeId") String routeId,
            @JsonProperty("order") int order,
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("failPolicy") FilterFailPolicy failPolicy,
            @JsonProperty("config") Map<String, Object> config
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.routeId = routeId;
        this.order = order;
        this.enabled = enabled;
        this.failPolicy = failPolicy == null ? FilterFailPolicy.FAIL_CLOSE : failPolicy;
        this.config = config == null ? Map.of() : Collections.unmodifiableMap(config);
    }

    @JsonProperty("name")
    public String name() {
        return name;
    }

    @JsonProperty("routeId")
    public String routeId() {
        return routeId;
    }

    @JsonProperty("order")
    public int order() {
        return order;
    }

    @JsonProperty("enabled")
    public boolean enabled() {
        return enabled;
    }

    @JsonProperty("failPolicy")
    public FilterFailPolicy failPolicy() {
        return failPolicy;
    }

    @JsonProperty("config")
    public Map<String, Object> config() {
        return config;
    }
}
