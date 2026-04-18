package io.homeey.gateway.plugin.api;

import io.homeey.gateway.common.context.Attributes;
import io.homeey.gateway.transport.api.HttpRequestMessage;
import io.homeey.gateway.transport.api.HttpResponseMessage;

import java.util.Objects;

public class GatewayContext {
    private final Attributes attributes = new Attributes();
    private HttpRequestMessage request;
    private HttpResponseMessage response;
    private String routeId;
    private String traceId;

    public Attributes attributes() {
        return attributes;
    }

    public HttpRequestMessage request() {
        return request;
    }

    public GatewayContext request(HttpRequestMessage request) {
        this.request = Objects.requireNonNull(request, "request");
        return this;
    }

    public HttpResponseMessage response() {
        return response;
    }

    public GatewayContext response(HttpResponseMessage response) {
        this.response = response;
        return this;
    }

    public String routeId() {
        return routeId;
    }

    public GatewayContext routeId(String routeId) {
        this.routeId = routeId;
        return this;
    }

    public String traceId() {
        return traceId;
    }

    public GatewayContext traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
}
