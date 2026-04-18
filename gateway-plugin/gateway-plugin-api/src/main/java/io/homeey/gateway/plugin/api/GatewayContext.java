package io.homeey.gateway.plugin.api;

import io.homeey.gateway.common.context.Attributes;

public class GatewayContext {
    private final Attributes attributes = new Attributes();

    public Attributes attributes() {
        return attributes;
    }
}
